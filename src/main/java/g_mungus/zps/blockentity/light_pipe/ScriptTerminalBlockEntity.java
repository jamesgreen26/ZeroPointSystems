package g_mungus.zps.blockentity.light_pipe;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScriptTerminalBlockEntity extends NetworkTerminalImpl implements LightPipeDataSender, ScriptComputer {
    public ScriptTerminalBlockEntity(BlockPos arg2, BlockState arg3) {
        super(ModBlockEntities.SCRIPT_TERMINAL.get(), arg2, arg3);
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
            currentCommand = resolveCoordinates(command);
            updateSignal(level);
            tickDelay = delay - 1; // delay value from GUI (2t, 4t, 8t, or 16t)
        }
    }

    private String resolveCoordinates(String command) {
        if (!(level instanceof ServerLevel serverLevel)) return command;
        Direction direction = this.getBlockState().getValue(ScriptTerminalBlock.FACING);
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

        String[] words = command.split(" ");
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            boolean replaced = false;
            if (i + 2 < words.length) {
                String w0 = words[i], w1 = words[i + 1], w2 = words[i + 2];
                boolean allRelative = w0.startsWith("~") && w1.startsWith("~") && w2.startsWith("~");
                boolean allLocal = w0.startsWith("^") && w1.startsWith("^") && w2.startsWith("^");
                if (allRelative || allLocal) {
                    String triplet = w0 + " " + w1 + " " + w2;
                    try {
                        BlockPos pos = BlockPosArgument.blockPos()
                                .parse(new StringReader(triplet))
                                .getBlockPos(sourceStack);
                        if (!result.isEmpty()) result.append(" ");
                        result.append(pos.getX()).append(" ").append(pos.getY()).append(" ").append(pos.getZ());
                        i += 3;
                        replaced = true;
                    } catch (CommandSyntaxException ignored) {}
                }
            }
            if (!replaced) {
                if (!result.isEmpty()) result.append(" ");
                result.append(words[i]);
                i++;
            }
        }
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
    }

    @Override
    public void load(net.minecraft.nbt.@NotNull CompoundTag tag) {
        super.load(tag);
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
    }

    @Override
    public net.minecraft.nbt.@NotNull CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        tag.putInt("Delay", delay);
        tag.putBoolean("WasPowered", wasPowered);
        return tag;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag) {
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
        delay = tag.getInt("Delay");
        wasPowered = tag.getBoolean("WasPowered");
    }

    @Override
    public String provideNextDisplayText(int length) {
        return currentCommand;
    }
}
