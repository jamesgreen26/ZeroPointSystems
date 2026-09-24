package g_mungus.zps.client.tractor;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.ClientCompat;
import g_mungus.zps.compat.RenderTransformProvider;
import g_mungus.zps.tractor.BeamGeometry;
import g_mungus.zps.tractor.BeamScan;
import g_mungus.zps.tractor.RunningBeams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

/**
 * Draws running Tractor Beams: the hull of each beam, column by column to wherever that column is stopped, in a
 * core shader with the colour and streaming stripes of the ion thruster's exhaust in Zero Point Labs, run toward
 * the mouth instead of away from a nozzle.
 * <p>
 * Additive and depth-tested but not depth-written, and drawn after block entities so that translucent blocks,
 * which do write depth, go over the beam rather than the beam failing against them. The hull is a handful of
 * quads, so it is built each frame rather than kept in a buffer.
 */
public final class TractorBeamRenderer {
    /**
     * Overall strength, as the alpha every vertex carries. The colour comes from the shader. Less than the
     * thruster's, which is a single thin plume: the beam is drawn with both its near and far walls showing, and
     * a wide one stacks several columns' worth of them.
     */
    private static final int STRENGTH = 150;
    /**
     * The thruster's clock is 64000 radians a day, which is a stripe passing about eight times a second. That
     * suits exhaust. The beam runs at a quarter of it: about two a second.
     */
    private static final double RADIANS_PER_TICK = 0.25 * 64000.0 / 24000.0;

    /** Steps to a block in the distance each vertex carries; tractor_beam.vsh divides by the same number. */
    private static final int DISTANCE_STEPS = 256;

    private static @Nullable ShaderInstance shader;

    private TractorBeamRenderer() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), ZPSMod.resource("tractor_beam"),
                        DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR),
                loaded -> shader = loaded);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ShaderInstance shader = TractorBeamRenderer.shader;
        ClientLevel level = Minecraft.getInstance().level;
        if (shader == null || level == null) {
            return;
        }
        // Running beams, and stopped ones still fading out.
        List<RunningBeams.Entry> beams = RunningBeams.visibleIn(level);
        if (beams.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        // Wrapped to a turn here, in doubles; see the shader.
        double now = level.getGameTime() + event.getPartialTick();
        double clock = now * RADIANS_PER_TICK;
        float streaming = (float) (clock % (2 * Math.PI));
        float winding = (float) ((clock / 100.0) % (2 * Math.PI));

        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // The hull's faces lie exactly on block boundaries: along the floor a beam skims, on the side of whatever
        // stopped the column next door, on the face of the block that stopped this one. Left at that they would
        // fight those blocks for depth. They are pulled forward in depth only, not moved: shifting the faces
        // themselves cannot be done consistently at every corner, and wherever it cannot, edges part and show.
        RenderSystem.polygonOffset(-2f, -20f);
        RenderSystem.enablePolygonOffset();

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        Matrix4d grid = new Matrix4d();
        Matrix4d local = new Matrix4d();
        Matrix4f pose = new Matrix4f();
        for (RunningBeams.Entry entry : beams) {
            BeamGeometry beam = entry.geometry();
            // Asked of every beam every frame, on screen or not, because asking is what moves the fade along.
            float glow = entry.glow(now);
            // Likewise: asking is what moves the drawn length along after the real one.
            float shown = entry.shownRange(now);
            if (glow <= 0f || shown <= 0f) {
                continue;
            }
            // A panel on a moving grid has its blocks in a far-off region of the level and is drawn wherever the
            // grid is this frame, so its bounds in the level say nothing about whether it is on screen.
            RenderTransformProvider provider = ClientCompat.renderTransformAt(level, beam.controller());
            boolean onGrid = provider != null && provider.localToWorld(grid) != null;
            if (!onGrid && !event.getFrustum().isVisible(entry.worldBounds())) {
                continue;
            }

            // The panel's grid to the camera's frame, in doubles while the numbers are world-sized.
            BlockPos origin = beam.controller();
            local.translation(-camera.x, -camera.y, -camera.z);
            if (onGrid) {
                local.mul(grid);
            }
            local.translate(origin.getX(), origin.getY(), origin.getZ());
            pose.set(local);

            shader.safeGetUniform("Glow").set(glow);
            shader.safeGetUniform("Phases").set(streaming, winding);
            // The colour and the fade run over the length drawn, so they stretch and shrink with it.
            shader.safeGetUniform("Panel").set(Math.max(shown, 1e-3f), (float) beam.width());
            BufferUploader.drawWithShader(buildHull(beam, entry.scanFor(level, shown), shown, pose));
        }

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * The beam's outside, measured from the controller. A column gets a wall on each side where it has no
     * neighbour or outreaches the one it has, and a cap on its far end.
     * <p>
     * The hull is one surface, though it is built a column at a time, and two things keep the joins from showing.
     * Every vertex is a whole number of blocks across the panel, and out from the mouth is either a whole number
     * too or the one length the beam is being drawn at, so neighbouring faces share their corners exactly,
     * whichever columns they belong to and whichever way they face. And every wall
     * is cut at every distance at which any column stops. Without that, a short column's wall would end partway
     * along the edge of its longer neighbour's, and an edge that meets a vertex only on one side is rasterised
     * with hairline cracks.
     */
    private static BufferBuilder.RenderedBuffer buildHull(BeamGeometry beam, BeamScan scan, float shown, Matrix4f pose) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
        int width = beam.width();
        Direction.Axis out = beam.facing().getAxis();
        Direction.Axis axisA = beam.axisA();
        Direction.Axis axisB = beam.axisB();
        // Along the facing axis the mouth plane is the controller's near or far face.
        double mouth = beam.facing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        double step = beam.facing().getAxisDirection().getStep();

        // Every distance at which some column stops, in order. Walls are cut at all of them.
        TreeSet<Float> stops = new TreeSet<>();
        for (int column = 0; column < scan.columns(); column++) {
            float reach = reach(scan, shown, width, column / width, column % width);
            if (reach > 0f) {
                stops.add(reach);
            }
        }

        for (int a = 0; a < width; a++) {
            for (int b = 0; b < width; b++) {
                float reach = reach(scan, shown, width, a, b);
                if (reach <= 0f) {
                    continue;
                }
                wall(builder, pose, beam, out, axisA, a, axisB, b, mouth, step,
                        reach(scan, shown, width, a - 1, b), reach, stops);
                wall(builder, pose, beam, out, axisA, a + 1, axisB, b, mouth, step,
                        reach(scan, shown, width, a + 1, b), reach, stops);
                wall(builder, pose, beam, out, axisB, b, axisA, a, mouth, step,
                        reach(scan, shown, width, a, b - 1), reach, stops);
                wall(builder, pose, beam, out, axisB, b + 1, axisA, a, mouth, step,
                        reach(scan, shown, width, a, b + 1), reach, stops);

                // End cap.
                quad(builder, pose, beam, out, mouth + step * reach, axisA, a, a + 1, axisB, b, b + 1, reach, reach);
            }
        }
        return builder.end();
    }

    /**
     * How far a column is drawn: as far as it reaches, but no further than {@code shown}, the length the beam is
     * being drawn at, which while the beam is changing length is not the length it has. Nothing, for a column
     * that is not there.
     */
    private static float reach(BeamScan scan, float shown, int width, int a, int b) {
        return a < 0 || b < 0 || a >= width || b >= width ? 0f : Math.min(scan.reach(a * width + b), shown);
    }

    /**
     * One side wall of a column, in the plane {@code across = acrossAt} and one block wide from {@code alongAt},
     * from where its neighbour on that side stops out to where it stops itself, as one quad per stretch between
     * {@code stops}.
     */
    private static void wall(BufferBuilder builder, Matrix4f pose, BeamGeometry beam, Direction.Axis out,
                             Direction.Axis across,
                             int acrossAt, Direction.Axis along, int alongAt, double mouth, double step,
                             float neighbourReach, float reach, TreeSet<Float> stops) {
        if (neighbourReach >= reach) {
            // The neighbour runs on as far or further: the wall between them is the neighbour's to draw.
            return;
        }
        float from = neighbourReach;
        for (float stop : stops.subSet(neighbourReach, false, reach, true)) {
            quad(builder, pose, beam, across, acrossAt, along, alongAt, alongAt + 1, out,
                    mouth + step * from, mouth + step * stop, from, stop);
            from = stop;
        }
    }

    /**
     * A quad lying in the plane {@code fixedAxis = fixed}, spanning {@code uAxis} and {@code vAxis}, measured from
     * the controller. {@code distance0} and {@code distance1} are how far out from the mouth its two {@code vAxis}
     * ends are, in blocks.
     */
    private static void quad(BufferBuilder builder, Matrix4f pose, BeamGeometry beam, Direction.Axis fixedAxis,
                             double fixed, Direction.Axis uAxis, double u0, double u1, Direction.Axis vAxis,
                             double v0, double v1, float distance0, float distance1) {
        vertex(builder, pose, beam, fixedAxis, fixed, uAxis, u0, vAxis, v0, distance0);
        vertex(builder, pose, beam, fixedAxis, fixed, uAxis, u1, vAxis, v0, distance0);
        vertex(builder, pose, beam, fixedAxis, fixed, uAxis, u1, vAxis, v1, distance1);
        vertex(builder, pose, beam, fixedAxis, fixed, uAxis, u0, vAxis, v1, distance1);
    }

    /**
     * One vertex, along with where it is in the beam's own terms, which is what the shader lays its pattern out
     * in: its place across the panel as the texture coordinate, and its distance out in the slot vanilla uses for
     * light. That slot holds whole numbers, which arrive exact, so the distance goes in 256ths of a block: fine
     * enough for a beam that is part-way through changing length, and two faces that share a vertex still agree
     * about it to the bit.
     */
    private static void vertex(BufferBuilder builder, Matrix4f pose, BeamGeometry beam, Direction.Axis fixedAxis,
                               double fixed, Direction.Axis uAxis, double u, Direction.Axis vAxis, double v,
                               float distance) {
        float x = (float) component(Direction.Axis.X, fixedAxis, fixed, uAxis, u, vAxis, v);
        float y = (float) component(Direction.Axis.Y, fixedAxis, fixed, uAxis, u, vAxis, v);
        float z = (float) component(Direction.Axis.Z, fixedAxis, fixed, uAxis, u, vAxis, v);
        // The controller is the panel's lowest corner, so a position measured from it is a place on the panel.
        float acrossA = (float) component(beam.axisA(), fixedAxis, fixed, uAxis, u, vAxis, v);
        float acrossB = (float) component(beam.axisB(), fixedAxis, fixed, uAxis, u, vAxis, v);
        builder.vertex(pose, x, y, z)
                .uv(acrossA, acrossB)
                .uv2(Math.round(distance * DISTANCE_STEPS), 0)
                .color(255, 255, 255, STRENGTH)
                .endVertex();
    }

    private static double component(Direction.Axis wanted, Direction.Axis fixedAxis, double fixed,
                                    Direction.Axis uAxis, double u, Direction.Axis vAxis, double v) {
        if (wanted == fixedAxis) {
            return fixed;
        }
        return wanted == uAxis ? u : v;
    }
}
