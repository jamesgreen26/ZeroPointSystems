package g_mungus.zps.client.exhaust;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exhaust particle physics, on the GPU. Each particle slot of the batch ring has a state (position
 * and velocity, and which batch it was started for) in a buffer the GPU steps itself: three
 * substeps a client tick, each one counting particles per block into {@link ExhaustField}'s
 * density, cloud-in-cell (the splat) and then moving every particle by transform feedback into another buffer
 * (the step). Nothing comes back to the CPU.
 *
 * <p>Three buffers rotate. At a tick, the current state becomes the previous one and is left
 * alone; the substeps pass between the other two. Drawing reads the previous and current states
 * and blends them by the partial tick, as vanilla does an entity's old and new position.
 *
 * <p>The programs are this class's own, built from {@code assets/zps/flywheel/exhaust/sim}, the
 * same tree Flywheel reads the drawing shaders from, so both sides include one {@code common.glsl}.
 *
 * <p>Render thread only. Every GL state touched is put back exactly as found, raw, so
 * GlStateManager's record of it stays true.
 */
final class ExhaustSimulation {

    static final int SUBSTEPS = 3;
    /** Bytes per slot: position, velocity, and the spawn tick, alive flag and collision count. */
    static final int SLOT_BYTES = 48;
    /** Texels per slot in the state's RGBA32UI texture buffers. */
    static final int SLOT_TEXELS = 3;
    /** The particles per tick the push of crowding is tuned for. */
    private static final float WEIGHT_REFERENCE = 16f;

    // Texture units: above every one Flywheel binds. 15 holds the batch ring. 11 is within
    // vanilla's twelve, but only its 2D binding is vanilla's; the grids go on its buffer target.
    private static final int UNIT_GRIDS = 11;
    private static final int UNIT_INDEX = 14;
    private static final int UNIT_TERRAIN = 13;
    private static final int UNIT_DENSITY = 12;
    private static final String SAMPLER_PREV = "zps_exhaustStatePrev";
    private static final String SAMPLER_CUR = "zps_exhaustStateCur";

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);

    private static int stepProgram;
    private static int splatProgram;
    private static boolean programsFailed;
    private static int stepTick;
    private static int stepStart;
    private static int stepLength;
    private static int splatWeight;
    private static int stepGridSubstep;

    private static int vertexArray;
    private static final int[] buffers = new int[3];
    private static final int[] textures = new int[3];
    private static int slots;
    private static int prev = 0;
    private static int cur = 1;

    private ExhaustSimulation() {
    }

    /** Sizes the state for a ring of this many slots, all of them unstarted. */
    static void allocate(int slotCount) {
        if (slotCount == slots && buffers[0] != 0) {
            return;
        }
        slots = slotCount;
        int previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        ByteBuffer zeros = MemoryUtil.memCalloc(slots * SLOT_BYTES);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_INDEX);
        int previousTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        for (int i = 0; i < 3; i++) {
            if (buffers[i] == 0) {
                buffers[i] = GL15.glGenBuffers();
                textures[i] = GL11.glGenTextures();
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers[i]);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, zeros, GL15.GL_DYNAMIC_COPY);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textures[i]);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32UI, buffers[i]);
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousTexture);
        GL13.glActiveTexture(active);
        MemoryUtil.memFree(zeros);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArray);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previousBuffer);
        prev = 0;
        cur = 1;
    }

    /** The most slots one texture buffer can show at once, as this driver allows. */
    static int maxSlots() {
        return GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE) / SLOT_TEXELS;
    }

    /** Programs are rebuilt from the resources on next use. */
    static void onResourcesReloaded() {
        deletePrograms();
        programsFailed = false;
    }

    /**
     * Steps every particle through one tick, ending at {@code tick}: the batch ring must already
     * hold this tick's records.
     *
     * @param batchesTexture the batch ring's texture buffer
     * @param batchSize      particles per batch, for the density weight
     */
    static void step(int tick, int batchesTexture, int batchSize) {
        if (slots == 0 || !programs()) {
            return;
        }
        SavedState saved = SavedState.save();
        try {
            bindFieldTextures(batchesTexture);
            int newPrev = cur;
            int a = (newPrev + 1) % 3;
            int b = (newPrev + 2) % 3;
            int source = newPrev;
            for (int substep = 0; substep < SUBSTEPS; substep++) {
                int target = substep % 2 == 0 ? a : b;
                splat(source, batchSize);
                advance(source, target, tick, substep);
                source = target;
            }
            prev = newPrev;
            cur = source;
        } finally {
            saved.restore();
        }
    }

    /**
     * Points a Flywheel program's state samplers at the previous and current states, alongside
     * the batch ring; called each time Flywheel binds a program that has them.
     */
    static void bindForDrawing(GlProgram program, int prevUnit, int curUnit) {
        if (buffers[0] == 0 || program.getUniformLocation(SAMPLER_CUR) < 0) {
            return;
        }
        program.setSamplerBinding(SAMPLER_PREV, prevUnit);
        program.setSamplerBinding(SAMPLER_CUR, curUnit);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + prevUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textures[prev]);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + curUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textures[cur]);
        GL13.glActiveTexture(active);
    }

    // Passes -------------------------------------------------------------------------------------

    /** Counts the particles in {@code source} per block into the field's density. */
    private static void splat(int source, int batchSize) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ExhaustField.densityFramebuffer());
        GL11.glViewport(0, 0, ExhaustField.DENSITY_WIDTH, ExhaustField.DENSITY_HEIGHT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0f, 0f, 0f, 0f});
        // Not sampled while drawn into.
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_DENSITY);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GL20.glUseProgram(splatProgram);
        GL20.glUniform1f(splatWeight, WEIGHT_REFERENCE / batchSize);
        attributes(source);
        // Cloud-in-cell: eight points per particle, one per nearest block centre. The particle's
        // state steps per instance; the stepping pass reads it per vertex again.
        for (int attribute = 0; attribute < 3; attribute++) {
            GL33.glVertexAttribDivisor(attribute, 1);
        }
        GL31.glDrawArraysInstanced(GL11.GL_POINTS, 0, 8, slots);
        for (int attribute = 0; attribute < 3; attribute++) {
            GL33.glVertexAttribDivisor(attribute, 0);
        }
    }

    /** Moves every particle one substep, from {@code source} into {@code target}. */
    private static void advance(int source, int target, int tick, int substep) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_DENSITY);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ExhaustField.densityTexture());
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        GL20.glUseProgram(stepProgram);
        GL30.glUniform1ui(stepTick, tick);
        GL20.glUniform1f(stepStart, -1f + (float) substep / SUBSTEPS);
        GL20.glUniform1f(stepLength, 1f / SUBSTEPS);
        GL20.glUniform1i(stepGridSubstep, substep);
        attributes(source);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffers[target]);
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL11.glDrawArrays(GL11.GL_POINTS, 0, slots);
        GL30.glEndTransformFeedback();
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
    }

    private static void attributes(int source) {
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers[source]);
        GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, SLOT_BYTES, 0L);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, SLOT_BYTES, 16L);
        GL30.glVertexAttribIPointer(2, 4, GL11.GL_UNSIGNED_INT, SLOT_BYTES, 32L);
    }

    private static void bindFieldTextures(int batchesTexture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 15);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, batchesTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_INDEX);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, ExhaustField.indexTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_TERRAIN);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, ExhaustField.terrainTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_GRIDS);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, ExhaustGrids.texture());
    }

    // Programs -----------------------------------------------------------------------------------

    private static boolean programs() {
        if (stepProgram != 0) {
            return true;
        }
        if (programsFailed) {
            return false;
        }
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try {
            stepProgram = link("sim/step.vert", "sim/discard.frag", true);
            splatProgram = link("sim/splat.vert", "sim/splat.frag", false);
            stepTick = GL20.glGetUniformLocation(stepProgram, "zps_tick");
            stepStart = GL20.glGetUniformLocation(stepProgram, "zps_stepStart");
            stepLength = GL20.glGetUniformLocation(stepProgram, "zps_stepLength");
            splatWeight = GL20.glGetUniformLocation(splatProgram, "zps_splatWeight");
            stepGridSubstep = GL20.glGetUniformLocation(stepProgram, "zps_gridSubstep");
            for (int program : new int[]{stepProgram, splatProgram}) {
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "zps_exhaustBatches"), 15);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "zps_fieldIndex"), UNIT_INDEX);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "zps_fieldTerrain"), UNIT_TERRAIN);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "zps_fieldDensity"), UNIT_DENSITY);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "zps_grids"), UNIT_GRIDS);
            }
            if (vertexArray == 0) {
                int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                vertexArray = GL30.glGenVertexArrays();
                GL30.glBindVertexArray(vertexArray);
                for (int attribute = 0; attribute < 3; attribute++) {
                    GL20.glEnableVertexAttribArray(attribute);
                }
                GL30.glBindVertexArray(previousArray);
            }
            return true;
        } catch (RuntimeException | IOException e) {
            ZPSMod.LOGGER.error("Exhaust simulation shaders failed; exhaust will not move", e);
            deletePrograms();
            programsFailed = true;
            return false;
        } finally {
            GL20.glUseProgram(previous);
        }
    }

    private static int link(String vertex, String fragment, boolean feedback) throws IOException {
        int program = GL20.glCreateProgram();
        int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragment);
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glBindAttribLocation(program, 0, "zps_inPosition");
        GL20.glBindAttribLocation(program, 1, "zps_inVelocity");
        GL20.glBindAttribLocation(program, 2, "zps_inMeta");
        if (feedback) {
            GL30.glTransformFeedbackVaryings(program,
                    new CharSequence[]{"zps_outPosition", "zps_outVelocity", "zps_outMeta"}, GL30.GL_INTERLEAVED_ATTRIBS);
        }
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Linking " + vertex + " failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String path) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, "#version 330 core\n" + source(path, new HashSet<>()));
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Compiling " + path + " failed: " + log);
        }
        return shader;
    }

    /** A shader's source with its includes expanded, each file once, as Flywheel resolves them. */
    private static String source(String path, Set<String> included) throws IOException {
        ResourceLocation location = ZPSMod.resource("flywheel/exhaust/" + path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("Missing shader source " + location);
        }
        String text;
        try (InputStream in = resource.get().open()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = INCLUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            // "zps:exhaust/common.glsl" is assets/zps/flywheel/exhaust/common.glsl, as for Flywheel.
            String include = matcher.group(1);
            String relative = include.substring(include.indexOf(':') + 1).replaceFirst("^exhaust/", "");
            String replacement = included.add(relative) ? source(relative, included) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void deletePrograms() {
        if (stepProgram != 0) {
            GL20.glDeleteProgram(stepProgram);
        }
        if (splatProgram != 0) {
            GL20.glDeleteProgram(splatProgram);
        }
        stepProgram = 0;
        splatProgram = 0;
    }

    /** Every piece of GL state the passes touch, as it was before them. */
    private record SavedState(int program, int vertexArray, int arrayBuffer, int framebuffer, int[] viewport,
                              boolean blend, int[] blendFunc, int[] blendEquation, boolean depthTest, boolean cull,
                              boolean scissor, boolean rasterizerDiscard, boolean[] colorMask, int activeTexture,
                              int[] unitBindings) {

        /** Units and targets the passes bind: 11 to 15, as 2D, 3D and buffer textures. */
        private static final int FIRST_UNIT = 11;
        private static final int UNITS = 5;
        private static final int[] TARGETS = {GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_3D, GL31.GL_TEXTURE_BUFFER};
        private static final int[] BINDINGS = {GL11.GL_TEXTURE_BINDING_2D, GL12.GL_TEXTURE_BINDING_3D, GL31.GL_TEXTURE_BINDING_BUFFER};

        static SavedState save() {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] units = new int[UNITS * TARGETS.length];
            for (int unit = 0; unit < UNITS; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + FIRST_UNIT + unit);
                for (int t = 0; t < TARGETS.length; t++) {
                    units[unit * TARGETS.length + t] = GL11.glGetInteger(BINDINGS[t]);
                }
            }
            GL13.glActiveTexture(activeTexture);
            byte[] mask = new byte[4];
            ByteBuffer maskBuffer = MemoryUtil.memAlloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBuffer);
            maskBuffer.get(mask);
            MemoryUtil.memFree(maskBuffer);
            return new SavedState(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport,
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    new int[]{GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                            GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)},
                    new int[]{GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)},
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),
                    new boolean[]{mask[0] != 0, mask[1] != 0, mask[2] != 0, mask[3] != 0},
                    activeTexture,
                    units);
        }

        void restore() {
            for (int unit = 0; unit < UNITS; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + FIRST_UNIT + unit);
                for (int t = 0; t < TARGETS.length; t++) {
                    GL11.glBindTexture(TARGETS[t], unitBindings[unit * TARGETS.length + t]);
                }
            }
            GL13.glActiveTexture(activeTexture);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            toggle(GL11.GL_BLEND, blend);
            GL14.glBlendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
            GL20.glBlendEquationSeparate(blendEquation[0], blendEquation[1]);
            toggle(GL11.GL_DEPTH_TEST, depthTest);
            toggle(GL11.GL_CULL_FACE, cull);
            toggle(GL11.GL_SCISSOR_TEST, scissor);
            toggle(GL30.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }

        private static void toggle(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }
    }

}
