package g_mungus.zps.blockentity.light_pipe;

import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.RedstoneBandSource;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.commands.content.TerminalLines;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.CreateCompat;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
 * network when it differs from what it last sent.
 *
 * <p>In EXECUTE mode it sends only to answer: after running {@code return}, it puts
 * {@code provide_value <value>} on the cable once the rest of the cable goes blank, for one tick,
 * as the terminal does its commands. It is a {@link #isReactive reactive} sender for that.
 *
 * <p>Keeps a record of the last command it ran and how that went, mirrored to clients for the
 * block's screen: the command text, whether it succeeded, and if not a plain-language reason with
 * the offending part of the command marked. The record only travels when it changes, so a bus fed
 * the same command every tick costs nothing on the wire.
 */
public class SerialBusBlockEntity extends AbstractTextDataReceiver implements LightPipeDataSender, RedstoneBandSource {

    private static final String MODE_TAG = "Mode";
    private static final String LAST_COMMAND_TAG = "LastCommand";
    private static final String LAST_OUTCOME_TAG = "LastOutcome";
    private static final String LAST_FAILURE_TAG = "LastFailure";
    private static final String EXPRESSION_TAG = "Expression";
    private static final String SENT_VALUE_TAG = "SentValue";
    private static final String REDSTONE_OUTPUT_TAG = "RedstoneOutput";

    /** How often a bus in GET mode reads the block it faces. */
    private static final int TICK_INTERVAL = 4;

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
    /** What {@code return} asked the bus to answer with, sent once the rest of the cable is blank. */
    private @Nullable String pendingAnswer;
    /** The answer while it is on the pipe, for the tick it went on in. */
    private @Nullable String answering;
    /** The game time the answer went on the pipe. */
    private long answeredAt;
    /** The power {@code set_redstone} last asked for, emitted into the block the bus faces. */
    private int redstoneOutput;

    private int tickCounter;

    public SerialBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERIAL_BUS.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        // In GET mode the bus is the one talking; whatever else is on the pipe is not ours to run.
        // An answer is a value, not a command: its prefix says so to every bus that hears it.
        if (mode == SerialBusMode.GET || TerminalLines.providedValue(message) != null) {
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

    // --- answering ------------------------------------------------------------------------------

    /** From {@code return}: the value to answer with when the terminal's output next clears. */
    public void setPendingAnswer(String value) {
        pendingAnswer = value;
    }

    /**
     * In EXECUTE mode the bus answers when the rest of the cable goes blank: its pending answer goes
     * on as {@code provide_value <value>} in that same signal change, and comes off a tick later,
     * or sooner if the cable would carry something else. In GET mode it sends its reading whatever
     * the cable carries.
     */
    @Override
    public boolean isReactive() {
        return mode != SerialBusMode.GET;
    }

    @Override
    public void respondTo(CableSignal prospective) {
        // Whichever update comes after the answer's tick takes it off, so answers that went on
        // together come off together, and none is left alone on the cable.
        if (answering != null && answerExpired()) {
            answering = null;
        }
        if (!prospective.isBlank()) {
            answering = null;
            pendingAnswer = null;
        } else if (pendingAnswer != null) {
            answering = TerminalLines.provideValue(pendingAnswer);
            answeredAt = level == null ? 0 : level.getGameTime();
            pendingAnswer = null;
        }
    }

    private boolean answerExpired() {
        return level != null && level.getGameTime() > answeredAt;
    }

    /** What the bus puts on the pipe: its reading in GET mode, otherwise only an answer. */
    private String outgoing() {
        if (mode == SerialBusMode.GET) {
            return sentValue;
        }
        return answering == null ? "" : answering;
    }

    private void executeCommand(String message) {
        if (!(level instanceof ServerLevel serverLevel) || message.isBlank()) {
            return;
        }
        String command = message.startsWith("/") ? message.substring(1) : message;
        ScriptCommandFailure failure = ZPSScripts.get()
                .tryRun(command, createCommandSourceStack(serverLevel), getAffectedBlockPos());
        recordOutcome(command, failure);
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

    // --- redstone ------------------------------------------------------------------------------

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    @Override
    public int getBandLevel() {
        return redstoneOutput;
    }

    /** Server only. Emits {@code power} into the faced block, as a repeater pointed into it would. */
    public void setRedstoneOutput(int power) {
        if (level == null || level.isClientSide || power == redstoneOutput) {
            return;
        }
        redstoneOutput = power;
        setChanged();
        // Clients draw the output as the band around the bus.
        syncToClients();
        SerialBusBlock.updateNeighborsInFront(level, worldPosition, getBlockState());
    }

    public String getCurrentText() {
        return currentDisplayText;
    }

    // --- GET mode ----------------------------------------------------------------------------

    /** Server side, from the block's ticker. Only a bus in GET mode has anything to do. */
    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // An answer is on the cable for one tick, like a terminal's command.
        if (answering != null && answerExpired()) {
            updateSignal(level);
        }
        if (mode != SerialBusMode.GET) {
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
            value = ZPSScripts.get().evaluate(chain, BuiltInTypes.STRING, createCommandSourceStack(serverLevel),
                    getAffectedBlockPos());
        } catch (Exception e) {
            // Positions come back in the expression's own terms, which is what the screen marks up.
            recordOutcome(chain, ZPSScripts.get().describe(e, chain, chain, SourceMap.IDENTITY));
            send(serverLevel, "");
            return;
        }
        recordOutcome(chain, null);
        send(serverLevel, value == null ? "" : value);
    }

    /** Puts a value on the pipe, walking the network only when it is not the one already there. */
    private void send(ServerLevel serverLevel, String value) {
        String value_parsed = value.replace("\\n", "\n");

        if (value_parsed.equals(sentValue)) {
            return;
        }
        sentValue = value_parsed;
        setChanged();
        updateSignal(serverLevel);
    }

    // --- sending ------------------------------------------------------------------------------

    @Override
    public String provideNextDisplayText(int length) {
        // Blank senders do not count towards a network's signal, so a bus that is executing stays
        // invisible to everything else on the pipe, but while it answers.
        String text = outgoing();
        return text.substring(0, Math.min(text.length(), length));
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
        boolean wasAnswering = answering != null;
        pendingAnswer = null;
        answering = null;
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
        if (wasAnswering && level != null) {
            updateSignal(level);
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
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        saveRecord(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        loadRecord(tag);
    }

    private void saveRecord(CompoundTag tag) {
        tag.putString(MODE_TAG, mode.getSerializedName());
        tag.putString(LAST_COMMAND_TAG, lastCommand);
        tag.putString(LAST_OUTCOME_TAG, lastOutcome.name());
        tag.putString(EXPRESSION_TAG, expression);
        tag.putString(SENT_VALUE_TAG, sentValue);
        tag.putInt(REDSTONE_OUTPUT_TAG, redstoneOutput);
        if (lastFailure != null) {
            tag.put(LAST_FAILURE_TAG, lastFailure.save(new CompoundTag()));
        }
    }

    private void loadRecord(CompoundTag tag) {
        mode = SerialBusMode.byName(tag.getString(MODE_TAG));
        lastCommand = tag.getString(LAST_COMMAND_TAG);
        expression = tag.getString(EXPRESSION_TAG);
        sentValue = tag.getString(SENT_VALUE_TAG);
        redstoneOutput = tag.getInt(REDSTONE_OUTPUT_TAG);
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

    /** The mode and the last command's record go to clients, for the screen, and the output for the band. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveRecord(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        loadRecord(tag);
    }
}
