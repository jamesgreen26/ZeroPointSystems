package g_mungus.zps.client.exhaust.debug;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.exhaust.ThrusterExhaustEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Emits one batch of exhaust a tick while its {@link ThrusterBlock} is powered, out of its facing
 * side, the signal strength its throttle.
 */
public class ThrusterBlockEntity extends BlockEntity implements ThrusterExhaustEmitter {

    private static final float NOZZLE_WIDTH = 0.25f;

    public ThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THRUSTER.get(), pos, state);
    }

    @Override
    public @Nullable ExhaustParams getCurrentExhaust() {
        BlockState state = getBlockState();
        int power = state.getValue(ThrusterBlock.POWER);
        if (power == 0) {
            return null;
        }
        float strength = power / 15f;

        Vec3 direction = Vec3.atLowerCornerOf(state.getValue(ThrusterBlock.FACING).getNormal());
        return new ExhaustParams(
                strength,
                NOZZLE_WIDTH,
                strength,
                Vec3.atCenterOf(worldPosition).add(direction.scale(0.55)),
                direction,
                ExhaustColor.ORANGE
        );
    }
}
