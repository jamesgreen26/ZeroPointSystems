package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.api_impl.CommandTreeBuilder;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.CreateCompat;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Turns the text arriving on its light pipe into script commands against the block it faces.
 *
 * <p>Keeps a record of the last command it ran and how that went, mirrored to clients for the
 * block's screen: the command text, whether it succeeded, and if not a plain-language reason with
 * the offending part of the command marked. The record only travels when it changes, so a bus fed
 * the same command every tick costs nothing on the wire.
 */
public class SerialBusBlockEntity extends AbstractTextDataReceiver {

    private static final String MODE_TAG = "Mode";
    private static final String LAST_COMMAND_TAG = "LastCommand";
    private static final String LAST_OUTCOME_TAG = "LastOutcome";
    private static final String LAST_FAILURE_TAG = "LastFailure";

    /** How the last command went. {@link #NONE} until the bus has run anything. */
    public enum Outcome {
        NONE, SUCCESS, FAILURE
    }

    private SerialBusMode mode = SerialBusMode.EXECUTE;
    private String lastCommand = "";
    private Outcome lastOutcome = Outcome.NONE;
    private @Nullable ScriptCommandFailure lastFailure;

    public SerialBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERIAL_BUS.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        boolean suppressCommand = shouldSuppressCommandsForDisplayLink();
        if (!suppressCommand && mode == SerialBusMode.EXECUTE) {
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
        if (!(level instanceof ServerLevel serverLevel) || message.isBlank()) {
            return;
        }
        String command = message.startsWith("/") ? message.substring(1) : message;
        String executed = ZPSCommands.Paths.SCRIPT + getPosArgument() + command;
        CommandSourceStack sourceStack = createCommandSourceStack(serverLevel);
        ScriptCommandFailure failure = executeCommand(serverLevel, sourceStack, command, executed);
        recordOutcome(command, failure);
    }

    /** Runs the command and reports why it failed, or null if it ran. */
    private @Nullable ScriptCommandFailure executeCommand(ServerLevel serverLevel, CommandSourceStack sourceStack,
                                                          String command, String executed) {
        try {
            var commands = serverLevel.getServer().getCommands();
            var parseResults = commands.getDispatcher().parse(executed, sourceStack);
            commands.getDispatcher().execute(parseResults);
            return null;
        } catch (Exception e) {
            ScriptCommandFailure failure = ScriptCommandFailure.describe(e, command, executed);
            if (ZPSConfig.getScriptCommandFailureBehavior() == ZPSConfig.ScriptCommandFailureBehavior.LOG
                    && !CommandTreeBuilder.isLoggedScriptCommandException(e)) {
                ZPSMod.LOGGER.error("Script command failed\nCommand: /{}\nReason: {}", executed, failure.reason());
            }
            return failure;
        }
    }

    private void recordOutcome(String command, @Nullable ScriptCommandFailure failure) {
        Outcome outcome = failure == null ? Outcome.SUCCESS : Outcome.FAILURE;
        if (command.equals(lastCommand) && outcome == lastOutcome && Objects.equals(failure, lastFailure)) {
            return;
        }
        lastCommand = command;
        lastOutcome = outcome;
        lastFailure = failure;
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private boolean shouldSuppressCommandsForDisplayLink() {
        return level != null
                && Compat.isCreateLoaded()
                && CreateCompat.isActiveSerialBusDisplayLinkSource(level, getAffectedBlockPos(), getBlockPos());
    }

    public String getCurrentText() {
        return currentDisplayText;
    }

    // --- settings and record, valid on both sides ------------------------------------------

    public SerialBusMode getMode() {
        return mode;
    }

    /** What the screen sends. Server only. */
    public void setMode(SerialBusMode newMode) {
        if (level == null || level.isClientSide || newMode == mode) {
            return;
        }
        mode = newMode;
        setChanged();
        syncToClients();
    }

    /** The last command the bus ran, without its leading slash; empty until it has run one. */
    public String getLastCommand() {
        return lastCommand;
    }

    public Outcome getLastOutcome() {
        return lastOutcome;
    }

    /** Why the last command failed, or null if it ran or nothing has run yet. */
    public @Nullable ScriptCommandFailure getLastFailure() {
        return lastFailure;
    }

    // --- command plumbing -------------------------------------------------------------------

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

    // --- persistence and sync ---------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        saveRecord(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        loadRecord(tag);
    }

    private void saveRecord(CompoundTag tag) {
        tag.putString(MODE_TAG, mode.getSerializedName());
        tag.putString(LAST_COMMAND_TAG, lastCommand);
        tag.putString(LAST_OUTCOME_TAG, lastOutcome.name());
        if (lastFailure != null) {
            tag.put(LAST_FAILURE_TAG, lastFailure.save(new CompoundTag()));
        }
    }

    private void loadRecord(CompoundTag tag) {
        mode = SerialBusMode.byName(tag.getString(MODE_TAG));
        lastCommand = tag.getString(LAST_COMMAND_TAG);
        lastOutcome = Outcome.NONE;
        try {
            if (tag.contains(LAST_OUTCOME_TAG, Tag.TAG_STRING)) {
                lastOutcome = Outcome.valueOf(tag.getString(LAST_OUTCOME_TAG));
            }
        } catch (IllegalArgumentException ignored) {
        }
        lastFailure = tag.contains(LAST_FAILURE_TAG, Tag.TAG_COMPOUND)
                ? ScriptCommandFailure.load(tag.getCompound(LAST_FAILURE_TAG))
                : null;
    }

    /** The mode and the last command's record go to clients, for the screen. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveRecord(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        loadRecord(tag);
    }
}
