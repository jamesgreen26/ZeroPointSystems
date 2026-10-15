package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.client.screens.ControllerClientHooks;
import g_mungus.zps.entity.ControllerSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Using an Octo- or Dodeca-Controller: sneak with an empty hand to set its keys, otherwise step on, or
 * off again if this is the controller being ridden.
 */
final class ControllerInteraction {
    private ControllerInteraction() {}

    static InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(ModBlocks.DENSE_CABLES.get().asItem()) || itemInHand.is(ModBlocks.DENSE_CABLE_SEPARATOR.get().asItem())) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ControllerBlockEntity<?> controller)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (hand != InteractionHand.MAIN_HAND || !itemInHand.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                ControllerClientHooks.openScreen(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.getVehicle() instanceof ControllerSeatEntity seat && seat.blockEntity == controller) {
            player.stopRiding();
            return InteractionResult.CONSUME;
        }
        controller.startRiding(player, false, pos, state, (ServerLevel) level);
        return InteractionResult.CONSUME;
    }
}
