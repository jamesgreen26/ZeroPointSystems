package g_mungus.zps.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.networking.GasDebugS2CPacket;
import g_mungus.zps.networking.RequestGasDebugC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Development overlay: tints every gas node by its pressure, so a network's state is visible
 * without opening a single GUI.
 *
 * <p>Only reachable when {@link ZPSConfig#showGasPressureOverlay()} is on, and that option only
 * exists outside a production environment.
 */
public final class GasPressureOverlay {

    /** How far around the player to ask about. */
    private static final int RADIUS = 48;
    /** Ticks between snapshot requests. Fast enough to follow a running machine, cheap enough to leave on. */
    private static final int REFRESH_TICKS = 10;

    /**
     * Pressure at the top of the colour ramp, in Pascals — Kelvin's default pipe ceiling, so a duct
     * at bursting point reads red.
     */
    private static final double FULL_SCALE_PRESSURE = 16_375_049.0;

    private static final float ALPHA = 0.35f;
    /** Drawn slightly proud of the block so it does not z-fight with the duct model. */
    static final double INFLATE = 0.02;

    private static final Map<BlockPos, Float> SAMPLES = new HashMap<>();

    private static int cooldown;

    private GasPressureOverlay() {
    }

    public static void accept(List<GasDebugS2CPacket.Sample> samples) {
        SAMPLES.clear();
        for (GasDebugS2CPacket.Sample sample : samples) {
            SAMPLES.put(sample.pos(), sample.pressure());
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer player) || minecraft.level == null) {
            return;
        }
        if (!ZPSConfig.showGasPressureOverlay()) {
            if (!SAMPLES.isEmpty()) {
                SAMPLES.clear();
            }
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = REFRESH_TICKS;
        ZPSGamePackets.sendToServer(new RequestGasDebugC2SPacket(player.blockPosition(), RADIUS));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || SAMPLES.isEmpty() || !ZPSConfig.showGasPressureOverlay()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugFilledBox());

        var camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (Map.Entry<BlockPos, Float> entry : SAMPLES.entrySet()) {
            BlockPos pos = entry.getKey();
            int colour = colourFor(entry.getValue());
            float red = ((colour >> 16) & 0xFF) / 255f;
            float green = ((colour >> 8) & 0xFF) / 255f;
            float blue = (colour & 0xFF) / 255f;

            double[] box = boxFor(pos, SAMPLES.keySet());
            LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    box[0], box[1], box[2], box[3], box[4], box[5],
                    red, green, blue, ALPHA);
        }

        poseStack.popPose();
        buffer.endBatch(RenderType.debugFilledBox());
    }

    /**
     * The box to draw for one node, as {@code minX, minY, minZ, maxX, maxY, maxZ}.
     *
     * <p>Every face is pushed {@link #INFLATE} proud of the block so the tint does not z-fight with
     * the duct model inside it — except a face that another node is sitting against, which is
     * trimmed back flush. Two neighbours inflated toward each other would otherwise overlap in a
     * shell of doubly-blended, depth-equal fragments right where a run of duct is most crowded.
     * Flush, they meet on one shared plane and backface culling picks a side.
     */
    static double[] boxFor(BlockPos pos, Set<BlockPos> occupied) {
        return new double[]{
                pos.getX() - inflation(pos, Direction.WEST, occupied),
                pos.getY() - inflation(pos, Direction.DOWN, occupied),
                pos.getZ() - inflation(pos, Direction.NORTH, occupied),
                pos.getX() + 1 + inflation(pos, Direction.EAST, occupied),
                pos.getY() + 1 + inflation(pos, Direction.UP, occupied),
                pos.getZ() + 1 + inflation(pos, Direction.SOUTH, occupied)
        };
    }

    private static double inflation(BlockPos pos, Direction direction, Set<BlockPos> occupied) {
        return occupied.contains(pos.relative(direction)) ? 0.0 : INFLATE;
    }

    /**
     * Blue at rest through to red at the ceiling. The ramp is logarithmic because working pressures
     * span several orders of magnitude — on a linear scale everything below a megapascal would be
     * the same shade of blue.
     */
    static int colourFor(double pressure) {
        double scaled = Math.log10(Math.max(pressure, 1.0)) / Math.log10(FULL_SCALE_PRESSURE);
        float t = (float) Mth.clamp(scaled, 0.0, 1.0);
        // Hue from 240 degrees (blue) down to 0 (red).
        return Mth.hsvToRgb((1.0f - t) * (240f / 360f), 1.0f, 1.0f);
    }
}
