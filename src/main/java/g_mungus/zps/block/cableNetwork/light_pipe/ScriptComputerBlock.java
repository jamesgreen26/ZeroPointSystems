package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.blockentity.light_pipe.ScriptComputer;
import g_mungus.zps.blockentity.light_pipe.ScriptComputerBlockEntity;
import g_mungus.zps.networking.ScriptComputerS2CPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScriptComputerBlock extends Block implements EntityBlock {


    public ScriptComputerBlock(Properties arg) {
        super(arg);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new ScriptComputerBlockEntity(arg, arg2);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull InteractionResult use(@NotNull BlockState arg, Level arg2, @NotNull BlockPos arg3, @NotNull Player arg4, @NotNull InteractionHand arg5, @NotNull BlockHitResult arg6) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof ScriptComputer scriptComputer) {
            if (arg4 instanceof ServerPlayer serverPlayer) {
                ScriptComputerS2CPacket packet = new ScriptComputerS2CPacket(
                    arg3,
                    scriptComputer.getLoop(),
                    scriptComputer.getValue()
                );
                ZPSGamePackets.INSTANCE.sendTo(packet, serverPlayer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
            return InteractionResult.sidedSuccess(arg2.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }
}
