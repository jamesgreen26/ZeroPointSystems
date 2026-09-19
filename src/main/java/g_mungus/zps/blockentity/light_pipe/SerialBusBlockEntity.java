package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.api_impl.CommandTreeBuilder;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Turns the text arriving on its light pipe into script commands against the block it faces, or —
 * in {@link SerialBusMode#GET} — reads a value back out of that block and puts it on the pipe.
 *
 * <p>In GET mode the bus stops listening and starts talking: every {@link #TICK_INTERVAL} ticks it
 * evaluates its expression, a getter to mapper chain yielding a string, and pushes the result to the
 * network when it differs from what it last sent. It is the only block that both receives and sends
 * on {@link Channels#MAIN}, so several inherited helpers need to know to skip the bus itself.
 *
 * <p>Keeps a record of the last command it ran and how that went, mirrored to clients for the
 * block's screen: the command text, whether it succeeded, and if not a plain-language reason with
 * the offending part of the command marked. The record only travels when it changes, so a bus fed
 * the same command every tick costs nothing on the wire.
 */
public class SerialBusBlockEntity extends AbstractTextDataReceiver implements LightPipeDataSender {

    private static final String MODE_TAG = "Mode";
    private static final String LAST_COMMAND_TAG = "LastCommand";
    private static final String LAST_OUTCOME_TAG = "LastOutcome";
    private static final String LAST_FAILURE_TAG = "LastFailure";
    private static final String EXPRESSION_TAG = "Expression";
    private static final String SENT_VALUE_TAG = "SentValue";

    /** How often a bus in GET mode reads the block it faces. */
    private static final int TICK_INTERVAL = 4;

    /** The type a GET expression has to yield: what goes on the pipe is text. */
    private static final ResourceLocation STRING_TYPE = ResourceLocation.parse("zps:string");

    /** What turns a value of some other type into the text the pipe carries. */
    private static final String AS_STRING_MAPPER = "as_string";

    /** How the last command went. {@link #NONE} until the bus has run anything. */
    public enum Outcome {
        NONE, SUCCESS, FAILURE
    }

    private SerialBusMode mode = SerialBusMode.EXECUTE;
    private String lastCommand = "";
    private Outcome lastOutcome = Outcome.NONE;
    private @Nullable ScriptCommandFailure lastFailure;

    /** The getter to mapper chain a bus in GET mode evaluates, as the player typed it. */
    private String expression = "";
    /** The last value put on the pipe. Kept across a reload so downstream displays survive one. */
    private String sentValue = "";

    private int tickCounter;
    /** Guards against a network that leads back here pushing us into our own updateSignal. */
    private boolean updating;

    public SerialBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERIAL_BUS.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        // In GET mode the bus is the one talking; whatever else is on the pipe is not ours to run.
        if (mode == SerialBusMode.GET) {
            return;
        }
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

    // --- GET mode ----------------------------------------------------------------------------

    /** Server side, from the block's ticker. Only a bus in GET mode has anything to do. */
    public void tick() {
        if (mode != SerialBusMode.GET || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        evaluateAndPush(serverLevel);
    }

    /**
     * Reads the block the bus faces through the expression and puts the answer on the pipe.
     *
     * <p>A value that has not changed is not sent again, so a steady reading costs one evaluation
     * and nothing else. A bus with nothing to read, or a chain that will not evaluate, sends the
     * empty string: what is downstream shows that the reading has stopped rather than a value
     * frozen in time, and the screen explains why.
     */
    private void evaluateAndPush(ServerLevel serverLevel) {
        String chain = expression.strip();
        if (chain.isEmpty()) {
            send(serverLevel, "");
            return;
        }
        String value;
        try {
            value = evaluateAsText(serverLevel, chain);
        } catch (Exception e) {
            // Positions come back in the expression's own terms, which is what the screen marks up.
            recordOutcome(chain, ScriptCommandFailure.describe(e, chain, chain));
            send(serverLevel, "");
            return;
        }
        recordOutcome(chain, null);
        send(serverLevel, value == null ? "" : value);
    }

    /** Puts a value on the pipe, walking the network only when it is not the one already there. */
    private void send(ServerLevel serverLevel, String value) {
        if (value.equals(sentValue)) {
            return;
        }
        sentValue = value;
        setChanged();
        updateSignal(serverLevel);
    }

    /**
     * The chain's value as text. A chain that stops short of text — {@code pos} rather than
     * {@code pos as_string} — is finished off with {@code as_string} rather than refused, since
     * that mapper is the only way its value could have reached the pipe anyway.
     *
     * @throws Exception the original failure if the chain does not read as text and {@code as_string}
     *                   cannot make it, that being the one worth explaining
     */
    private String evaluateAsText(ServerLevel serverLevel, String chain) throws Exception {
        try {
            return new ValueOfExpression<String>(chain, STRING_TYPE)
                    .evaluate(createCommandSourceStack(serverLevel), getAffectedBlockPos());
        } catch (Exception e) {
            try {
                return new ValueOfExpression<String>(chain + " " + AS_STRING_MAPPER, STRING_TYPE)
                        .evaluate(createCommandSourceStack(serverLevel), getAffectedBlockPos());
            } catch (Exception ignored) {
                throw e;
            }
        }
    }

    // --- sending ------------------------------------------------------------------------------

    @Override
    public String provideNextDisplayText(int length) {
        if (mode != SerialBusMode.GET) {
            // Blank senders do not count towards a network's signal, so a bus that is executing
            // stays invisible to everything else on the pipe, exactly as it was before GET existed.
            return "";
        }
        return sentValue.substring(0, Math.min(sentValue.length(), length));
    }

    /**
     * The inherited walk would hand the bus its own output and count the bus as competing with
     * itself, since it sends and receives on one channel. This one skips the bus on both counts.
     */
    @Override
    public void updateSignal(Level level) {
        if (updating) {
            return;
        }
        updating = true;
        try {
            boolean clear = isClearSignal(level);
            for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
                if (terminal.pos().equals(getBlockPos())) continue;
                if (!(level.getBlockEntity(terminal.pos()) instanceof LightPipeDataReceiver.Text receiver)) {
                    continue;
                }
                String text = clear ? sentValue : garbled(receiver.getMaxLength());
                receiver.acceptText(terminal.channel(), text.substring(0, Math.min(text.length(), receiver.getMaxLength())));
            }
        } finally {
            updating = false;
        }
    }

    /** As the interface's own check, but counting the bus once from its own field rather than twice. */
    private boolean isClearSignal(Level level) {
        int senders = sentValue.isBlank() ? 0 : 1;
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            if (terminal.pos().equals(getBlockPos())) continue;
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender sender && !sender.provideNextDisplayText(1000).isBlank()
                    && ++senders > 1) {
                return false;
            }
        }
        return true;
    }

    private static String garbled(int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append((char) (33 + (int) (Math.random() * 94)));
        }
        return out.toString();
    }

    /** Stops sending and blanks whatever the bus last put on the pipe. */
    public void clearSentValue() {
        if (sentValue.isEmpty() || level == null || level.isClientSide) {
            return;
        }
        sentValue = "";
        setChanged();
        updateSignal(level);
    }

    /** A bus that already has a value hands it to whatever just joined the network. */
    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null && !level.isClientSide && mode == SerialBusMode.GET && !sentValue.isEmpty()) {
            updateSignal(level);
        }
    }

    /** The bus is a sender now, so it must not count as one when asking whether it has one. */
    @Override
    protected boolean hasSender(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            if (terminal.pos().equals(getBlockPos())) continue;
            if (level.getBlockEntity(terminal.pos()) instanceof LightPipeDataSender) {
                return true;
            }
        }
        return false;
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
        // The record describes what the old mode was doing, and the counter belongs to a run that
        // is over. Leaving GET also takes the bus's value off the pipe, so displays do not sit on
        // a reading that nothing is refreshing any more.
        lastCommand = "";
        lastOutcome = Outcome.NONE;
        lastFailure = null;
        tickCounter = 0;
        if (newMode != SerialBusMode.GET) {
            clearSentValue();
        }
        setChanged();
        syncToClients();
    }

    /** The GET expression, as the screen sends it. Server only. */
    public void setExpression(String newExpression) {
        if (level == null || level.isClientSide || newExpression.equals(expression)) {
            return;
        }
        expression = newExpression;
        // The old verdict was about the old text.
        lastCommand = "";
        lastOutcome = Outcome.NONE;
        lastFailure = null;
        tickCounter = 0;
        setChanged();
        syncToClients();
    }

    /** The getter to mapper chain this bus evaluates in GET mode. */
    public String getExpression() {
        return expression;
    }

    /** What the bus last put on the pipe. */
    public String getSentValue() {
        return sentValue;
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

    /** The block the bus acts on: the one it faces. */
    public @NotNull BlockPos getAffectedBlockPos() {
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
        tag.putString(EXPRESSION_TAG, expression);
        tag.putString(SENT_VALUE_TAG, sentValue);
        if (lastFailure != null) {
            tag.put(LAST_FAILURE_TAG, lastFailure.save(new CompoundTag()));
        }
    }

    private void loadRecord(CompoundTag tag) {
        mode = SerialBusMode.byName(tag.getString(MODE_TAG));
        lastCommand = tag.getString(LAST_COMMAND_TAG);
        expression = tag.getString(EXPRESSION_TAG);
        sentValue = tag.getString(SENT_VALUE_TAG);
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
