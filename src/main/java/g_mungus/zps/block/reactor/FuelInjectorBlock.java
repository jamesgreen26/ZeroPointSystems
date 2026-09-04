package g_mungus.zps.block.reactor;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.reactor.FuelInjectorBlockEntity;
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
 * Lets gas into the chamber and never out. The outer face admits gas inward only; the reactor
 * adds a one-way edge from the stub into the chamber, so fuel arrives only while the supply line
 * is at a higher pressure than the chamber.
 */
public class FuelInjectorBlock extends ReactorGasWallBlock {

    public FuelInjectorBlock(Properties properties) {
        super(properties);
    }

    /** Inward: from the neighbour on the outer face into this block. */
    @Override
    protected Direction allowedFlow(Direction facing) {
        return facing.getOpposite();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.FUEL_INJECTOR.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlockEntities.FUEL_INJECTOR.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((FuelInjectorBlockEntity) blockEntity).serverTick();
    }
}
