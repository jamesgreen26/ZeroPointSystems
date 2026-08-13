package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.api_impl.CommandTreeBuilder;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.CreateCompat;
import g_mungus.zps.config.ZPSConfig;
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
        boolean suppressCommand = shouldSuppressCommandsForDisplayLink();
        if (!suppressCommand) {
            executeCommand(message);
        }
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
        if (suppressCommand && level instanceof ServerLevel serverLevel) {
            CreateCompat.tickDisplayLinkSource(serverLevel, getAffectedBlockPos());
        }
    }

    private void executeCommand(String message) {
        if (level instanceof ServerLevel serverLevel) {
            String command = message.startsWith("/") ? message.substring(1) : message;
            command = ZPSCommands.Paths.SCRIPT + getPosArgument() + command;
            CommandSourceStack sourceStack = createCommandSourceStack(serverLevel);
            executeCommand(serverLevel, sourceStack, command);
        }
    }

    private void executeCommand(ServerLevel serverLevel, CommandSourceStack sourceStack, String command) {
        try {
            var commands = serverLevel.getServer().getCommands();
            var parseResults = commands.getDispatcher().parse(command, sourceStack);
            commands.getDispatcher().execute(parseResults);
        } catch (Exception e) {
            if (ZPSConfig.getScriptCommandFailureBehavior() == ZPSConfig.ScriptCommandFailureBehavior.LOG
                    && !CommandTreeBuilder.isLoggedScriptCommandException(e)) {
                ZPSMod.LOGGER.error("Script command failed\nCommand: /{}\nReason: {}", command, describeException(e));
            }
        }
    }

    private static String describeException(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private boolean shouldSuppressCommandsForDisplayLink() {
        return level != null
                && Compat.isCreateLoaded()
                && CreateCompat.isActiveSerialBusDisplayLinkSource(level, getAffectedBlockPos(), getBlockPos());
    }

    public String getCurrentText() {
        return currentDisplayText;
    }

    private String getPosArgument() {
        BlockPos pos = getAffectedBlockPos();
        return " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " ";
    }

    private @NotNull BlockPos getAffectedBlockPos() {
        return getBlockPos().offset(getBlockState().getValue(TransformerBlock.FACING).getNormal());
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
