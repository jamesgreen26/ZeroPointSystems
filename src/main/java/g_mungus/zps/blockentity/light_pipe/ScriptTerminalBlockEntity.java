package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ScriptTerminalBlockEntity extends NetworkTerminalImpl implements LightPipeDataSender, ScriptComputer {
    public ScriptTerminalBlockEntity(BlockPos arg2, BlockState arg3) {
        super(ModBlockEntities.SCRIPT_TERMINAL.get(), arg2, arg3);
    }

    private String allCommands = "";
    private String currentCommand = "";
    private boolean loop = false;
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
        if (shouldContinue || shouldRestart) {
            if (tickDelay <= 0) {
                currentCommand = commands.get(head);
                updateSignal(level);
                head++;
                tickDelay = 3; // advance every fourth tick
            } else {
                tickDelay--;
            }
        } else if (!currentCommand.isEmpty()) {
            currentCommand = "";
            updateSignal(level);
        }

        wasPowered = powered;
    }



    @Override
    public void acceptUpdatePacket(ScriptComputerC2SPacket packet) {
        allCommands = packet.contents();
        loop = packet.loop();
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
    protected void saveAdditional(net.minecraft.nbt.@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
    }

    @Override
    public void load(net.minecraft.nbt.@NotNull CompoundTag tag) {
        super.load(tag);
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
    }

    @Override
    public net.minecraft.nbt.@NotNull CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        tag.putString("AllCommands", allCommands);
        tag.putBoolean("Loop", loop);
        return tag;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag) {
        allCommands = tag.getString("AllCommands");
        loop = tag.getBoolean("Loop");
    }

    @Override
    public String provideNextDisplayText(int length) {
        return currentCommand;
    }
}
