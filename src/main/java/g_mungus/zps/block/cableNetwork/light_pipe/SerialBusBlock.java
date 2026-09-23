package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.client.screens.SerialBusClientHooks;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
     * Cladding the cable comes first, as on every cable; any other click opens the bus's screen.
     * No inventory, so nothing for a container menu to hold: the screen is opened straight from
     * the client, the way the reactor port's is.
     * <p>
     * Holding a piece of the wiring itself is a build action, not a request for the screen: a
     * data cable, script terminal, text display or any cladding item falls through so the block
     * is placed against the bus instead.
     */
    @Override
    protected InteractionResult useComponent(BlockState state, Level level, BlockPos pos, Player player,
                                             InteractionHand hand, BlockHitResult hit) {
        InteractionResult clad = super.useComponent(state, level, pos, player, hand, hit);
        if (clad != InteractionResult.PASS) {
            return clad;
        }
        if (isBuildItem(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            SerialBusClientHooks.openScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean isBuildItem(ItemStack stack) {
        return stack.is(ModItems.DATA_CABLE.get())
            || stack.is(ModItems.SCRIPT_TERMINAL.get())
            || stack.is(ModItems.TEXT_DISPLAY.get())
            || stack.is(ModItems.CABLE_INSULATION.get())
            || stack.is(ModItems.CATWALK.get())
            || stack.is(ModItems.SPACE_GRATING_BLOCK.get());
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

    /**
     * Breaking a bus that was sending takes its value off the pipe, so downstream displays do not
     * hold a reading that nothing is refreshing. The block entity has to be asked before the
     * removal takes it away.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SerialBusBlockEntity bus) {
            bus.clearSentValue();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
