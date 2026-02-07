package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.zps.commands.SetRedstoneCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {

    @WrapMethod(method = "getSignal")
    private int getSignalWrap(BlockState arg, BlockGetter arg2, BlockPos arg3, Direction arg4, Operation<Integer> original) {
        int i = original.call(arg, arg2, arg3, arg4);
        if (arg2 instanceof ServerLevel serverLevel) {
            int commandResult = SetRedstoneCommand.getRedstonePowerAt(serverLevel, arg3.offset(arg4.getOpposite().getNormal()));
            i = Math.max(i, commandResult);
        }
        return i;
    }
}
