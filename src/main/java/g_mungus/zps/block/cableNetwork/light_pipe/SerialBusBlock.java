package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.client.screens.SerialBusClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerialBusBlock extends TransformerBlock {
    public SerialBusBlock(Properties properties) {
        super(properties);
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
    }

    /**
     * Cladding the cable comes first, as on every cable. Otherwise only an empty main hand opens the
     * bus's screen; anything held goes on to its own use, such as placing a block against the bus,
     * and the off hand never opens it, so an item in the main hand cannot fall through to it. No
     * inventory, so nothing for a container menu to hold: the screen is opened straight from the
     * client, the way the reactor port's is.
     */
    @Override
    protected InteractionResult useComponent(BlockState state, Level level, BlockPos pos, Player player,
                                             InteractionHand hand, BlockHitResult hit) {
        InteractionResult clad = super.useComponent(state, level, pos, player, hand, hit);
        if (clad != InteractionResult.PASS) {
            return clad;
        }
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            SerialBusClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new SerialBusBlockEntity(arg, arg2);
    }

    /** Only a bus in Get mode has anything to do on a tick; the block entity decides that itself. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                 @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof SerialBusBlockEntity it) {
                it.tick();
            }
        };
    }

    // --- redstone ------------------------------------------------------------------------------

    /**
     * A bus emits what {@code set_redstone} last asked for out of its front face only, like a
     * repeater: the faced block is strongly powered, and nothing else around the bus is.
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction == state.getValue(FACING).getOpposite();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                               @NotNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                         @NotNull Direction direction) {
        if (direction != state.getValue(FACING).getOpposite()) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof SerialBusBlockEntity bus ? bus.getRedstoneOutput() : 0;
    }

    /** Tells the faced block, and what is around it, that the bus's output changed, as a repeater does. */
    public static void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos front = pos.relative(facing);
        level.neighborChanged(front, state.getBlock(), pos);
        level.updateNeighborsAtExceptFromFacing(front, state.getBlock(), facing.getOpposite());
    }

    /**
     * Breaking a bus that was sending takes its value off the pipe, so downstream displays do not
     * hold a reading that nothing is refreshing. The block entity has to be asked before the
     * removal takes it away.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean moved) {
        boolean wasPowering = false;
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SerialBusBlockEntity bus) {
            bus.clearSentValue();
            wasPowering = bus.getRedstoneOutput() > 0;
        }
        super.onRemove(state, level, pos, newState, moved);
        if (wasPowering) {
            updateNeighborsInFront(level, pos, state);
        }
    }
}
