package g_mungus.zps.client.exhaust;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.material.Material;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.io.IOException;

/**
 * Exhaust is drawn into a layer of its own and composited into the finished level, so that it sorts
 * against itself and against everything translucent the level draws after Flywheel: water, glass,
 * particles, clouds. Within the layer, weighted blended transparency (McGuire and Bavoil 2013) makes
 * the order particles are drawn in irrelevant, between thrusters as much as within one plume.
 *
 * <p>Each frame with exhaust in it:
 * <ol>
 *     <li>{@code AFTER_SKY}: if any section live exhaust can be in is in view, the layer opens for
 *     the frame, its targets sized to the main target. If none is, it stays shut: its passes cost
 *     several milliseconds, and Flywheel's instancing backend draws exhaust off screen too, so a
 *     draw alone does not mean anything shows.</li>
 *     <li>Flywheel's draws: {@link #afterSetup} sends the exhaust material's draws into the layer,
 *     one framebuffer with both targets as draw buffers, each blended as weighted transparency
 *     needs, clearing them at the first. The accumulation target sums weighted colour; the
 *     revealage target multiplies down transmittance and keeps the nearest exhaust depth. Both are
 *     tested against the main depth buffer, which they share.</li>
 *     <li>{@code AFTER_BLOCK_ENTITIES}: once every opaque thing is drawn, block entity renderers
 *     having drawn after Flywheel, one pass copies the depth buffer and the image, and the image's
 *     alpha is zeroed. Translucent blending adds each surface's alpha back as {@code a + d(1 - a)},
 *     so by the end of the level the alpha is how much of the opaque scene they cover.</li>
 *     <li>{@code AFTER_LEVEL}: three fullscreen passes blend the layer into the finished image, then
 *     the alpha is set back to opaque. Exhaust behind something opaque is dropped; in front of
 *     everything it goes over the image; behind something translucent it takes the place of the
 *     opaque scene that surface lets through, as though drawn before it.</li>
 * </ol>
 *
 * <p>On a tiled GPU, as Apple's are, every switch away from the main target and back stores the
 * whole image and depth buffer and loads them again, about a millisecond each at Retina sizes. So
 * the frame leaves the main target only twice: for the exhaust draws, and for the copy. Clears go
 * where the targets are bound anyway, and the composite blends rather than reading a copy.
 *
 * <p>Depths are compared per pixel at the nearest exhaust, so a translucent surface cutting through a
 * plume puts all of that pixel's exhaust on one side of it. With Fabulous graphics the level keeps
 * translucent terrain out of the main depth buffer and image, so exhaust always goes over it.
 *
 * <p>Blending each draw buffer its own way takes OpenGL 4.0. Without it there is no exhaust.
 */
public final class ExhaustLayer {

    /** Beyond any view distance, in blocks: what an empty pixel's nearest exhaust depth is. */
    private static final float NO_EXHAUST = 60000f;

    private static @Nullable ShaderInstance opaqueCopyShader;
    private static @Nullable ShaderInstance compositeShader;

    private static int width;
    private static int height;
    private static int sharedDepth;
    private static int accumTexture;
    private static int revealTexture;
    private static int opaqueTexture;
    private static int opaqueColorTexture;
    /** Draws to the accumulation target (draw buffer 0) and the revealage target (1). */
    private static int layerFramebuffer;
    /** Draws to the opaque depth (draw buffer 0) and opaque colour (1) copies. */
    private static int copyFramebuffer;

    /** Whether the targets are sized for this frame, so exhaust may be drawn into them. */
    private static boolean open;
    /** Whether exhaust was drawn this frame, so the targets have been cleared. */
    private static boolean drawn;
    /** Whether the image's alpha was zeroed this frame, and wants setting back. */
    private static boolean alphaZeroed;
    /** The framebuffer an exhaust draw replaced, while one is under way; 0 otherwise. */
    private static int redirectedFrom;
    private static boolean redirecting;
    /** Whether the driver can blend draw buffers separately; null until first asked. */
    private static @Nullable Boolean supported;

    private ExhaustLayer() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("exhaust_opaque_copy"),
                DefaultVertexFormat.POSITION), loaded -> opaqueCopyShader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("exhaust_composite"),
                DefaultVertexFormat.POSITION), loaded -> compositeShader = loaded);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            // Under a shader pack the pack composites exhaust itself (see ExhaustRendering).
            open = ExhaustParticles.active() && !ExhaustRendering.throughShaderPack()
                    && inView(event.getFrustum()) && prepare();
            drawn = false;
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // Flywheel resets its state after drawing, which ends any redirect; this is a backstop.
            endRedirect();
            if (drawn) {
                copyOpaque(event.getProjectionMatrix());
                setImageAlpha(0f);
                alphaZeroed = true;
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (alphaZeroed) {
                composite(event.getProjectionMatrix());
                setImageAlpha(1f);
                alphaZeroed = false;
            }
            open = false;
            drawn = false;
        }
    }

    /** Whether any section live exhaust can be in is inside the frustum. */
    private static boolean inView(Frustum frustum) {
        for (long section : ExhaustParticles.trackedSections()) {
            int x = SectionPos.sectionToBlockCoord(SectionPos.x(section));
            int y = SectionPos.sectionToBlockCoord(SectionPos.y(section));
            int z = SectionPos.sectionToBlockCoord(SectionPos.z(section));
            if (frustum.isVisible(new AABB(x, y, z, x + 16, y + 16, z + 16))) {
                return true;
            }
        }
        return false;
    }

    /** Before Flywheel sets up any draw: an exhaust draw before it is over. */
    public static void beforeSetup() {
        endRedirect();
    }

    /** After Flywheel sets up a draw: an exhaust draw goes to the layer, with the layer's blending. */
    public static void afterSetup(Material material) {
        if (material != ExhaustRendering.MATERIAL) {
            return;
        }
        if (!open) {
            // Nowhere to draw it: draw nothing rather than smear it over the level. Flywheel sets
            // the colour mask again for its next draw.
            RenderSystem.colorMask(false, false, false, false);
            return;
        }
        redirectedFrom = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        redirecting = true;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, layerFramebuffer);
        if (!drawn) {
            clearLayer();
        }
        // Accumulation, every draw buffer as far as Minecraft's state cache knows:
        // rgb += rgb * (a * w), alpha += a * w
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        // Revealage, past the cache, which endRedirect squares again: rgb *= 1 - a, alpha = min(alpha, depth)
        GL40.glBlendFuncSeparatei(1, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE);
        GL40.glBlendEquationSeparatei(1, GL14.GL_FUNC_ADD, GL14.GL_MIN);
        drawn = true;
    }

    /** After Flywheel has drawn everything. */
    public static void afterReset() {
        endRedirect();
    }

    private static void endRedirect() {
        if (!redirecting) {
            return;
        }
        redirecting = false;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, redirectedFrom);
        // Back to one blend for every draw buffer, the one the state cache holds, so the cache is
        // right for draw buffer 1 too: the cache skips calls that match it.
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    }

    private static boolean supported() {
        if (supported == null) {
            supported = GL.getCapabilities().OpenGL40;
            if (!supported) {
                ZPSMod.LOGGER.warn("Exhaust needs OpenGL 4.0 to blend its two targets separately; no exhaust will be drawn");
            }
        }
        return supported;
    }

    /** Sizes the targets to the main target, and clears them. False if they could not be made. */
    private static boolean prepare() {
        if (!supported()) {
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width != width || main.height != height) {
            delete();
            width = main.width;
            height = main.height;
            sharedDepth = main.getDepthTextureId();
            accumTexture = texture(GL30.GL_RGBA16F, GL11.GL_RGBA);
            revealTexture = texture(GL30.GL_RGBA16F, GL11.GL_RGBA);
            opaqueTexture = texture(GL30.GL_R32F, GL11.GL_RED);
            opaqueColorTexture = texture(GL11.GL_RGBA8, GL11.GL_RGBA);
            int depthAttachment = main.isStencilEnabled() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
            layerFramebuffer = framebuffer(depthAttachment, accumTexture, revealTexture);
            copyFramebuffer = framebuffer(0, opaqueTexture, opaqueColorTexture);
            if (layerFramebuffer == 0 || copyFramebuffer == 0) {
                delete();
                return false;
            }
        }
        return true;
    }

    /**
     * Clears the layer's targets, which must be the bound draw framebuffer: at the frame's first
     * exhaust draw, which binds them anyway, so the clear costs no trip away from the main target.
     */
    private static void clearLayer() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // The main depth texture is attached afresh every frame. Minecraft remakes the main target
        // on a GUI scale change even at the same size, and GL can give the new depth texture the
        // old one's name; a framebuffer keeps the deleted original alive, never cleared again, and
        // exhaust would be depth-tested against the last frame drawn into it, menus and all.
        sharedDepth = main.getDepthTextureId();
        int depthAttachment = main.isStencilEnabled() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, depthAttachment, GL11.GL_TEXTURE_2D, sharedDepth, 0);
        RenderSystem.colorMask(true, true, true, true);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0f, 0f, 0f, 0f});
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, new float[]{1f, 1f, 1f, NO_EXHAUST});
        if (scissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static int texture(int internalFormat, int format) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        ExhaustField.withPlainUnpack(() -> GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                format, GL11.GL_FLOAT, (java.nio.ByteBuffer) null));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        return id;
    }

    /**
     * A framebuffer drawing to {@code colors}, draw buffer by draw buffer, sharing the main depth
     * buffer unless {@code depthAttachment} is 0.
     */
    private static int framebuffer(int depthAttachment, int... colors) {
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        int[] drawBuffers = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, drawBuffers[i], GL11.GL_TEXTURE_2D, colors[i], 0);
        }
        GL20.glDrawBuffers(drawBuffers);
        if (depthAttachment != 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthAttachment, GL11.GL_TEXTURE_2D, sharedDepth, 0);
        }
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            ZPSMod.LOGGER.error("Exhaust layer framebuffer incomplete: 0x{}", Integer.toHexString(status));
            GL30.glDeleteFramebuffers(id);
            return 0;
        }
        return id;
    }

    private static void delete() {
        for (int framebuffer : new int[]{layerFramebuffer, copyFramebuffer}) {
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
            }
        }
        for (int texture : new int[]{accumTexture, revealTexture, opaqueTexture, opaqueColorTexture}) {
            if (texture != 0) {
                GlStateManager._deleteTexture(texture);
            }
        }
        layerFramebuffer = copyFramebuffer = 0;
        accumTexture = revealTexture = opaqueTexture = opaqueColorTexture = 0;
        width = height = sharedDepth = 0;
    }

    /** Copies the opaque scene's depth, as view distance, and its colour, in one pass. */
    private static void copyOpaque(Matrix4f projection) {
        ShaderInstance shader = opaqueCopyShader;
        if (shader == null) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        shader.setSampler("SceneDepth", main.getDepthTextureId());
        shader.setSampler("SceneColor", main.getColorTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        RenderSystem.disableBlend();
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFramebuffer);
        drawFullscreen(shader, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
    }

    /** Sets the alpha of the main target's whole image, leaving its colour be. */
    private static void setImageAlpha(float alpha) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        RenderSystem.colorMask(false, false, false, true);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0f, 0f, 0f, alpha});
        if (scissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        RenderSystem.colorMask(true, true, true, true);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
    }

    /**
     * Blends the layer into the finished image, in the three passes exhaust_composite.fsh sets
     * out. The image's alpha is left as it is throughout, as the later passes read it. Which pixels
     * each pass takes, the depth test decides, against the depth buffer as the level left it.
     */
    private static void composite(Matrix4f projection) {
        ShaderInstance shader = compositeShader;
        if (shader == null) {
            return;
        }
        shader.setSampler("Accum", accumTexture);
        shader.setSampler("Reveal", revealTexture);
        shader.setSampler("OpaqueDepth", opaqueTexture);
        shader.setSampler("Opaque", opaqueColorTexture);
        shader.safeGetUniform("ExhaustProjMat").set(projection);
        RenderSystem.enableBlend();
        // In front of everything: mix(image, exhaust, coverage).
        compositePass(shader, 0, GL11.GL_LEQUAL, GL14.GL_FUNC_ADD,
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // Behind translucent surfaces: take out the opaque scene first, then put the exhaust in.
        compositePass(shader, 1, GL11.GL_GREATER, GL14.GL_FUNC_REVERSE_SUBTRACT,
                GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA, GlStateManager.DestFactor.ONE);
        compositePass(shader, 2, GL11.GL_GREATER, GL14.GL_FUNC_ADD,
                GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void compositePass(ShaderInstance shader, int pass, int depthFunc, int equation,
                                      GlStateManager.SourceFactor source, GlStateManager.DestFactor dest) {
        shader.safeGetUniform("Pass").set(pass);
        RenderSystem.blendEquation(equation);
        // Alpha is kept: ZERO of the pass's, ONE of the image's.
        RenderSystem.blendFuncSeparate(source, dest, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        drawFullscreen(shader, depthFunc);
    }

    /** One quad over the whole target, never depth-written; depth-tested by {@code depthFunc} unless it is 0. */
    private static void drawFullscreen(ShaderInstance shader, int depthFunc) {
        if (depthFunc == 0) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(depthFunc);
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(-1f, -1f, 0f);
        quad.addVertex(1f, -1f, 0f);
        quad.addVertex(1f, 1f, 0f);
        quad.addVertex(-1f, 1f, 0f);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableDepthTest();
    }
}
