package g_mungus.zps.exhaust;

import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Emits one batch of exhaust a tick while its {@link ThrusterBlock} is powered and in view range. */
public class ThrusterBlockEntity extends BlockEntity {

    /** Beyond this many blocks from the camera a thruster stops emitting; what is in the air stays. */
    private static final double EMIT_DISTANCE = 128;
    /** Blocks per tick leaving the nozzle; drag brings the plume to rest about ten times this out. */
    private static final float EXHAUST_SPEED = 1.0f;

    public ThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THRUSTER.get(), pos, state);
    }

    void clientTick() {
        BlockState state = getBlockState();
        if (!state.getValue(ThrusterBlock.POWERED) || !(level instanceof ClientLevel clientLevel)
                || !ExhaustParticles.available(clientLevel)) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(Vec3.atCenterOf(worldPosition)) > EMIT_DISTANCE * EMIT_DISTANCE) {
            return;
        }
        Direction facing = state.getValue(ThrusterBlock.FACING);
        Vec3 direction = Vec3.atLowerCornerOf(facing.getNormal());
        ExhaustParticles.emit(clientLevel, worldPosition,
                new Vec3(0.5, 0.5, 0.5).add(direction.scale(0.55)),
                direction, EXHAUST_SPEED, Vec3.ZERO);
    }
}
