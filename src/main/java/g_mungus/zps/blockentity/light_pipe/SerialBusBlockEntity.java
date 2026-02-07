package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.ZPSCommands;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class SerialBusBlockEntity extends AbstractTextDataReceiver {
    public SerialBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERIAL_BUS.get(), pos, state);
    }


    @Override
    public void acceptText(int channel, String message) {
        executeCommand(message);
        if (!message.equals(currentDisplayText)) {
            currentDisplayText = message;
            setChanged();

            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(
                        worldPosition,
                        getBlockState(),
                        getBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private void executeCommand(String message) {
        if (level instanceof ServerLevel serverLevel) {
            String command = message.startsWith("/") ? message.substring(1) : message;
            command = ZPSCommands.PREFIX + getPosArgument() + command;
            serverLevel.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(serverLevel), command);
        }
    }

    private String getPosArgument() {
        BlockPos pos = getBlockPos().offset(getBlockState().getValue(TransformerBlock.FACING).getNormal());
        return " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " ";
    }

    public CommandSourceStack createCommandSourceStack(ServerLevel serverLevel) {
        Direction direction = this.getBlockState().getValue(CommandBlock.FACING);
        return new CommandSourceStack(
                new CommandSource() {
                    @Override
                    public void sendSystemMessage(@NotNull Component arg) {

                    }

                    @Override
                    public boolean acceptsSuccess() {
                        return true;
                    }

                    @Override
                    public boolean acceptsFailure() {
                        return true;
                    }

                    @Override
                    public boolean shouldInformAdmins() {
                        return false;
                    }
                },
                Vec3.atCenterOf(this.worldPosition),
                new Vec2(0.0F, direction.toYRot()),
                serverLevel,
                2,
                "zps:serial_bus",
                Component.literal("zps:serial_bus"),
                serverLevel.getServer(),
                null
        );
    }
}
