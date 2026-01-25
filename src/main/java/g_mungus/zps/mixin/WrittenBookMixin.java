package g_mungus.zps.mixin;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTransmitterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WrittenBookItem.class)
public class WrittenBookMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    public void useOnInject(UseOnContext arg, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = arg.getLevel();
        BlockPos blockPos = arg.getClickedPos();
        BlockState blockState = level.getBlockState(blockPos);
        if (blockState.is(ModBlocks.SCRIPT_TRANSMITTER.get())) {
            cir.setReturnValue(ScriptTransmitterBlock.tryPlaceBook(arg.getPlayer(), level, blockPos, blockState, arg.getItemInHand()) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS);
        }
    }
}
