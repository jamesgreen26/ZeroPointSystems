package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public class RoboticArmBlockEntityRenderer implements BlockEntityRenderer<RoboticArmBlockEntity> {
    public RoboticArmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RoboticArmBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Vec3 handPos = getInterpolatedHandPosition(blockEntity, partialTick);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        drawJointBox(poseStack, lines, handPos, 0.10f, 1.0f, 0.7f, 0.2f, 1.0f);
    }

    private static Vec3 getInterpolatedHandPosition(RoboticArmBlockEntity blockEntity, float partialTick) {
        Vec3 settled = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getHandBlockPos());
        if (!blockEntity.isMoving() || blockEntity.getLevel() == null) return settled;

        double elapsed = (blockEntity.getLevel().getGameTime() - blockEntity.getMoveStartTick()) + partialTick;
        double progress = Math.min(1.0, Math.max(0.0, elapsed / RoboticArmBlockEntity.MOVE_TIME_TICKS));
        Vec3 start = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveStartBlockPos());
        Vec3 end = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveTargetBlockPos());
        return start.lerp(end, progress);
    }

    private static Vec3 toLocalCenter(net.minecraft.core.BlockPos origin, net.minecraft.core.BlockPos target) {
        return new Vec3(
                (target.getX() - origin.getX()) + 0.5,
                (target.getY() - origin.getY()) + 0.5,
                (target.getZ() - origin.getZ()) + 0.5
        );
    }

    private static void drawJointBox(PoseStack poseStack, VertexConsumer lines, Vec3 center, float halfSize, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                center.x - halfSize,
                center.y - halfSize,
                center.z - halfSize,
                center.x + halfSize,
                center.y + halfSize,
                center.z + halfSize,
                r,
                g,
                b,
                a
        );
    }

    @Override
    public boolean shouldRenderOffScreen(RoboticArmBlockEntity p_112306_) {
        return true;
    }

    @Override
    public boolean shouldRender(RoboticArmBlockEntity p_173568_, Vec3 p_173569_) {
        return true;
    }
}
