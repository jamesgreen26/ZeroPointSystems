package g_mungus.zps.mixin;

import g_mungus.zps.commands.actions.SetRedstoneCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void sendBlockUpdatedMixin(BlockPos arg, BlockState arg2, BlockState arg3, int i, CallbackInfo ci) {
        if (!arg2.getBlock().equals(arg3.getBlock())) {
            SetRedstoneCommand.setRedstone((ServerLevel) (Object)this, arg, 0);
        }
    }
}
