package g_mungus.zps.blockentity.light_pipe;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import g_mungus.zps.item.AddressPadItem;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    }

    public ItemStack removeAddressPad() {
        ItemStack existing = addressPad;
        addressPad = ItemStack.EMPTY;
        syncHasAddressPadProperty();
        setChanged();
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

    public void tick() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof ScriptTerminalBlock) || level == null) return;
        boolean powered = blockState.getValue(ScriptTerminalBlock.POWERED);

        List<String> commands = Arrays.stream(allCommands.split("\n")).filter(it -> !it.isBlank()).toList();

        if (head >= commands.size()) head = 0;
        if (powered && !wasPowered) tickDelay = 0;

        boolean shouldContinue = head > 0;
        boolean shouldRestart = powered && (!wasPowered || loop);
        if ((shouldContinue || shouldRestart) && !commands.isEmpty()) {
            if (tickDelay <= 0) {
                String command = commands.get(head);
                if (command.startsWith("/")) command = command.substring(1);
                processCommand(command);
                head++;
            } else {
                tickDelay--;
            }
        } else clearOutput();

        wasPowered = powered;
    }

    @SuppressWarnings("deprecation")
    public Set<ResourceLocation> collectBlocks() {
        Set<ResourceLocation> out = new HashSet<>();
        if (level instanceof ServerLevel serverLevel) {
            for (var terminal : getTerminals(Channels.MAIN)) {
                BlockState blockState = serverLevel.getBlockState(terminal.pos());
                if (blockState.is(ModBlocks.SERIAL_BUS.get())) {
                    Block block = serverLevel.getBlockState(
                            terminal.pos().offset(blockState.getValue(SerialBusBlock.FACING).getNormal())
                    ).getBlock();
                    out.add(block.builtInRegistryHolder().key().location());
                }
            }
        }
        return out;
    }

    private void processCommand(String command) {
        if (command.startsWith("wait ")) {
            executeWaitCommand(command);
            clearOutput();
        } else {
            currentCommand = resolveAddresses(resolveCoordinates(command, level, worldPosition, getBlockState()));
            updateSignal(level);
            tickDelay = delay - 1; // delay value from GUI (2t, 4t, 8t, or 16t)
        }
    }

    private String resolveAddresses(String command) {
        if (!hasAddressPad()) return command;

        var entries = AddressPadItem.getSortedEntries(addressPad).stream()
                .sorted((a, b) -> Integer.compare(b.name().length(), a.name().length()))
                .toList();

        String result = command;
        for (AddressPadItem.Entry entry : entries) {
            String key = "@" + Pattern.quote(entry.name());
            BlockPos pos = entry.pos();
            String replacement = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            result = result.replaceAll("(?<![A-Za-z0-9._])" + key + "(?![A-Za-z0-9._])", replacement);
        }
        return result;
    }

    /// mostly just visible for testing
    public static String resolveCoordinates(String command, Level level, BlockPos worldPosition, BlockState blockState) {
        if (!(level instanceof ServerLevel serverLevel)) return command;
        Direction direction = blockState.getValue(ScriptTerminalBlock.FACING);
        CommandSourceStack sourceStack = new CommandSourceStack(
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

        // Tokenize by splitting on ( ) and spaces, preserving separators so they
        // can be reconstructed around any resolved coordinate triplets.
        List<String> tokens = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher m = Pattern.compile("[^() ]+").matcher(command);
        int lastEnd = 0;
        while (m.find()) {
            separators.add(command.substring(lastEnd, m.start()));
            tokens.add(m.group());
            lastEnd = m.end();
        }
        String trailingSep = command.substring(lastEnd);

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < tokens.size()) {
            boolean replaced = false;
            if (i + 2 < tokens.size()) {
                String w0 = tokens.get(i), w1 = tokens.get(i + 1), w2 = tokens.get(i + 2);
                boolean allRelative = w0.startsWith("~") && w1.startsWith("~") && w2.startsWith("~");
                boolean allLocal = w0.startsWith("^") && w1.startsWith("^") && w2.startsWith("^");
                if (allRelative || allLocal) {
                    String triplet = w0 + " " + w1 + " " + w2;
                    try {
                        BlockPos pos = BlockPosArgument.blockPos()
                                .parse(new StringReader(triplet))
                                .getBlockPos(sourceStack);
                        result.append(separators.get(i));
                        result.append(pos.getX()).append(separators.get(i + 1))
                              .append(pos.getY()).append(separators.get(i + 2))
                              .append(pos.getZ());
                        i += 3;
                        replaced = true;
                    } catch (CommandSyntaxException ignored) {}
                }
            }
            if (!replaced) {
                result.append(separators.get(i));
                result.append(tokens.get(i));
                i++;
            }
        }
        result.append(trailingSep);
        return result.toString();
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
    protected void saveAdditional(net.minecraft.nbt.@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        tag.putInt("Delay", delay);
        tag.putBoolean("WasPowered", wasPowered);
        if (!addressPad.isEmpty()) {
            tag.put("AddressPad", addressPad.save(new net.minecraft.nbt.CompoundTag()));
        }
    }

    @Override
    public void load(net.minecraft.nbt.@NotNull CompoundTag tag) {
        super.load(tag);
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("AddressPad", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            addressPad = ItemStack.of(tag.getCompound("AddressPad"));
        } else {
            addressPad = ItemStack.EMPTY;
        }
    }

    @Override
    public net.minecraft.nbt.@NotNull CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        tag.putInt("Delay", delay);
        tag.putBoolean("WasPowered", wasPowered);
        if (!addressPad.isEmpty()) {
            tag.put("AddressPad", addressPad.save(new net.minecraft.nbt.CompoundTag()));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag) {
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("AddressPad", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            addressPad = ItemStack.of(tag.getCompound("AddressPad"));
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
}
