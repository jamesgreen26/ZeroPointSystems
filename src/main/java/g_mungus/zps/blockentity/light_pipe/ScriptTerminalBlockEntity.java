package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.commands.preprocess.AddressPreProcessor;
import g_mungus.zps.commands.preprocess.CoordinatePreProcessor;
import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import g_mungus.zps.util.BookComponents;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScriptTerminalBlockEntity extends NetworkTerminalImpl implements LightPipeDataSender, ScriptComputer, Clearable {
    private ItemStack addressPad = ItemStack.EMPTY;
    private final Container addressPadAccess = new Container() {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return ScriptTerminalBlockEntity.this.addressPad.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? ScriptTerminalBlockEntity.this.addressPad : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot != 0) return ItemStack.EMPTY;
            ItemStack split = ScriptTerminalBlockEntity.this.addressPad.split(amount);
            if (ScriptTerminalBlockEntity.this.addressPad.isEmpty()) {
                ScriptTerminalBlockEntity.this.syncHasAddressPadProperty();
                ScriptTerminalBlockEntity.this.setChanged();
            }
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot != 0) return ItemStack.EMPTY;
            ItemStack existing = ScriptTerminalBlockEntity.this.addressPad;
            ScriptTerminalBlockEntity.this.addressPad = ItemStack.EMPTY;
            ScriptTerminalBlockEntity.this.syncHasAddressPadProperty();
            ScriptTerminalBlockEntity.this.setChanged();
            return existing;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot != 0) return;
            ScriptTerminalBlockEntity.this.addressPad = stack;
            ScriptTerminalBlockEntity.this.syncHasAddressPadProperty();
            ScriptTerminalBlockEntity.this.setChanged();
        }

        @Override
        public void setChanged() {
            ScriptTerminalBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ScriptTerminalBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            ScriptTerminalBlockEntity.this.addressPad = ItemStack.EMPTY;
            ScriptTerminalBlockEntity.this.syncHasAddressPadProperty();
            ScriptTerminalBlockEntity.this.setChanged();
        }
    };
    public ScriptTerminalBlockEntity(BlockPos arg2, BlockState arg3) {
        super(ModBlockEntities.SCRIPT_TERMINAL.get(), arg2, arg3);
    }

    public boolean hasAddressPad() {
        return !addressPad.isEmpty();
    }

    public ItemStack getAddressPad() {
        return addressPad;
    }

    public void setAddressPad(ItemStack addressPad) {
        this.addressPad = addressPad;
        syncHasAddressPadProperty();
        setChanged();
        syncClientState();
    }

    public ItemStack removeAddressPad() {
        ItemStack existing = addressPad;
        addressPad = ItemStack.EMPTY;
        syncHasAddressPadProperty();
        setChanged();
        syncClientState();
        return existing;
    }

    public Container getAddressPadAccess() {
        return addressPadAccess;
    }

    private String allCommands = "";
    private String currentCommand = "";
    private boolean loop = false;
    private int delay = 4;
    private boolean wasPowered = false;
    private int head = 0;
    private int tickDelay = 0;
    private @Nullable PreparedScript preparedScript = null;

    public void tick() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof ScriptTerminalBlock) || level == null) return;
        boolean powered = blockState.getValue(ScriptTerminalBlock.POWERED);

        if (!(level instanceof ServerLevel serverLevel)) return;
        PreparedScript script = getPreparedScript(serverLevel);
        List<String> commands = script.commands();

        if (head >= commands.size()) head = 0;
        if (powered && !wasPowered) tickDelay = 0;

        boolean shouldContinue = head > 0;
        boolean shouldRestart = powered && (!wasPowered || loop);
        if ((shouldContinue || shouldRestart) && !commands.isEmpty()) {
            if (tickDelay <= 0) {
                String command = commands.get(head);
                if (command.startsWith("/")) command = command.substring(1);
                processCommand(serverLevel, command, script, script.commandLines().get(head));
                head++;
            } else {
                tickDelay--;
            }
        } else clearOutput();

        wasPowered = powered;
    }

    /** What the Serial Buses on this terminal's network face: what its script can be aimed at. */
    public Set<ScriptTarget> collectTargets() {
        Set<ScriptTarget> out = new HashSet<>();
        if (level instanceof ServerLevel serverLevel) {
            for (var terminal : getTerminals(Channels.MAIN)) {
                BlockState blockState = serverLevel.getBlockState(terminal.pos());
                if (blockState.is(ModBlocks.SERIAL_BUS.get())) {
                    BlockPos faced = terminal.pos().offset(blockState.getValue(SerialBusBlock.FACING).getNormal());
                    out.add(ScriptTarget.at(serverLevel, faced));
                }
            }
        }
        return out;
    }

    /**
     * The script as it runs: its commands, with comments, alias definitions and blank lines left
     * out, the line each stands on, and the pre-processing each goes through, which uses only the
     * aliases defined above it. Prepared again when the script, the Address Pad or the server's
     * scripts change.
     */
    private record PreparedScript(String text, Map<String, BlockPos> addresses, ZPSScripts scripts,
                                  List<String> commands, List<Integer> commandLines,
                                  CommandPreProcessor.Prepared preProcessing) {
    }

    private PreparedScript getPreparedScript(ServerLevel serverLevel) {
        Map<String, BlockPos> addresses = getAddresses();
        ZPSScripts scripts = ZPSScripts.get();
        PreparedScript cached = preparedScript;
        if (cached != null && cached.text().equals(allCommands) && cached.addresses().equals(addresses)
                && cached.scripts() == scripts) {
            return cached;
        }
        CommandSourceStack origin = createTerminalCommandSourceStack(serverLevel, worldPosition, getBlockState());
        List<String> lines = allCommands.lines().toList();
        // A malformed alias definition is left out rather than stopping the script.
        CommandPreProcessor.Prepared preProcessing = scripts.prepare(lines, origin, worldPosition,
                List.of(new CoordinatePreProcessor(origin), new AddressPreProcessor(addresses)));
        List<String> commands = new ArrayList<>();
        List<Integer> commandLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!preProcessing.consumedLines().contains(i) && !lines.get(i).isBlank()) {
                commands.add(lines.get(i).strip());
                commandLines.add(i);
            }
        }
        PreparedScript prepared = new PreparedScript(allCommands, addresses, scripts, List.copyOf(commands),
                List.copyOf(commandLines), preProcessing);
        preparedScript = prepared;
        return prepared;
    }

    private void clearParsedScriptCache() {
        preparedScript = null;
    }

    /** @param line the script line the command stands on, whose aliases it uses */
    private void processCommand(ServerLevel serverLevel, String command, PreparedScript script, int line) {
        if (command.startsWith("wait ")) {
            executeWaitCommand(command);
            clearOutput();
        } else {
            CommandSourceStack origin = createTerminalCommandSourceStack(serverLevel, worldPosition, getBlockState());
            currentCommand = script.scripts().process(script.preProcessing().at(line), command, origin, worldPosition)
                    .command();
            updateSignal(level);
            tickDelay = delay - 1; // delay value from GUI (2t, 4t, 8t, or 16t)
        }
    }

    /** The Address Pad's entries by name, or none without a pad. */
    @Override
    public Map<String, BlockPos> getAddresses() {
        if (!hasAddressPad()) {
            return Map.of();
        }
        Map<String, BlockPos> addresses = new HashMap<>();
        for (AddressPadItem.Entry entry : AddressPadItem.getSortedEntries(addressPad)) {
            addresses.put(entry.name(), entry.pos());
        }
        return addresses;
    }

    /// mostly just visible for testing
    public static String resolveCoordinates(String command, Level level, BlockPos worldPosition, BlockState blockState) {
        if (!(level instanceof ServerLevel serverLevel)) return command;
        CoordinatePreProcessor coordinates = new CoordinatePreProcessor(
                createTerminalCommandSourceStack(serverLevel, worldPosition, blockState));
        return coordinates.prepare(List.of(), null).process(command, null).command();
    }

    private static CommandSourceStack createTerminalCommandSourceStack(ServerLevel serverLevel, BlockPos worldPosition, BlockState blockState) {
        Direction direction = blockState.getValue(ScriptTerminalBlock.FACING);
        return new CommandSourceStack(
                new CommandSource() {
                    @Override public void sendSystemMessage(@NotNull Component arg) {}
                    @Override public boolean acceptsSuccess() { return false; }
                    @Override public boolean acceptsFailure() { return false; }
                    @Override public boolean shouldInformAdmins() { return false; }
                },
                Vec3.atCenterOf(worldPosition),
                new Vec2(0.0F, direction.getOpposite().toYRot()),
                serverLevel,
                2,
                "zps:script_terminal",
                Component.literal("zps:script_terminal"),
                serverLevel.getServer(),
                null
        );
    }

    private void clearOutput() {
        if (!currentCommand.isEmpty()) {
            currentCommand = "";
            updateSignal(level);
        }
    }

    private void executeWaitCommand(String command) {
        try {
            int cycles = Integer.parseInt(command.substring(5));
            if (cycles > 0) {
                tickDelay = (delay * cycles) - 1;
            } else {
                tickDelay = delay - 1;
            }
        } catch (Exception e) {
            tickDelay = delay - 1;
        }
    }


    @Override
    public void acceptUpdatePacket(ScriptComputerC2SPacket packet) {
        allCommands = packet.contents();
        loop = packet.loop();
        delay = packet.delay();
        head = 0;
        clearParsedScriptCache();
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_ALL
            );
        }
    }

    @Override
    public BlockPos getPos() {
        return getBlockPos();
    }

    @Override
    public boolean canEdit(Vec3 eyePosition) {
        return !this.isRemoved(); //todo: distance check with VS compat
    }

    @Override
    public String getValue() {
        return allCommands;
    }

    @Override
    public boolean getLoop() {
        return loop;
    }

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        tag.putInt("Delay", delay);
        tag.putBoolean("WasPowered", wasPowered);
        if (!addressPad.isEmpty()) {
            tag.put("AddressPad", BookComponents.save(registries, addressPad));
        }
    }


    @Override
    protected void loadAdditional(net.minecraft.nbt.@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        allCommands = tag.getString("AllCommands");
        clearParsedScriptCache();
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("AddressPad", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            addressPad = BookComponents.parse(registries, tag.getCompound("AddressPad"));
        } else {
            addressPad = ItemStack.EMPTY;
        }
    }

    @Override
    public net.minecraft.nbt.@NotNull CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        tag.putInt("Delay", delay);
        tag.putBoolean("WasPowered", wasPowered);
        if (!addressPad.isEmpty()) {
            tag.put("AddressPad", BookComponents.save(registries, addressPad));
        }
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) {
        if (pkt != null && pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag(), registries);
        }
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        allCommands = tag.getString("AllCommands");
        clearParsedScriptCache();
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("AddressPad", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            addressPad = BookComponents.parse(registries, tag.getCompound("AddressPad"));
        } else {
            addressPad = ItemStack.EMPTY;
        }
        syncHasAddressPadProperty();
    }

    @Override
    public String provideNextDisplayText(int length) {
        return currentCommand;
    }

    @Override
    public void clearContent() {
        removeAddressPad();
    }

    private void syncHasAddressPadProperty() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ScriptTerminalBlock)) return;

        boolean hasAddressPad = !addressPad.isEmpty();
        if (state.getValue(ScriptTerminalBlock.HAS_ADDRESS_PAD) == hasAddressPad) return;

        ScriptTerminalBlock.resetAddressPadState(level, worldPosition, state, hasAddressPad);
    }

    private void syncClientState() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
