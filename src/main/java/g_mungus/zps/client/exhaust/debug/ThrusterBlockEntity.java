package g_mungus.zps.client.exhaust.debug;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.exhaust.ThrusterExhaustEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Emits one batch of exhaust a tick while its {@link ThrusterBlock} is powered, out of its facing
 * side.
 */
public class ThrusterBlockEntity extends BlockEntity implements ThrusterExhaustEmitter {

    /** Blocks per tick leaving the nozzle; drag brings the plume to rest about ten times this out. */
    private static final float EXHAUST_SPEED = 1.0f;

    public ThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THRUSTER.get(), pos, state);
    }

    @Override
    public boolean emitsExhaust() {
        return getBlockState().getValue(ThrusterBlock.POWERED);
    }

    @Override
    public Vec3 exhaustPosition() {
        return Vec3.atCenterOf(worldPosition).add(exhaustDirection().scale(0.55));
    }

    @Override
    public Vec3 exhaustDirection() {
        return Vec3.atLowerCornerOf(getBlockState().getValue(ThrusterBlock.FACING).getNormal());
    }

    @Override
    public float exhaustSpeed() {
        return EXHAUST_SPEED;
    }
}
