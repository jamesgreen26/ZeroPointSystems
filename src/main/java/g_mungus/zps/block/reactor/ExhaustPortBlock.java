package g_mungus.zps.block.reactor;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.reactor.ExhaustPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lets gas out of the chamber and never in. The block entity draws everything but Flux out of
 * the chamber at a fixed rate and cools it; the outer face passes gas outward only.
 */
public class ExhaustPortBlock extends ReactorGasWallBlock {

    public ExhaustPortBlock(Properties properties) {
        super(properties);
    }

    /** Outward: from this block to the neighbour on the outer face. */
    @Override
    protected Direction allowedFlow(Direction facing) {
        return facing;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.EXHAUST_PORT.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlockEntities.EXHAUST_PORT.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((ExhaustPortBlockEntity) blockEntity).serverTick();
    }
}
