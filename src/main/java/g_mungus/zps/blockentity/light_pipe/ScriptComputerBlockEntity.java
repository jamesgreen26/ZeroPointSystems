package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ScriptComputerBlockEntity extends BlockEntity implements ScriptComputer {
    public ScriptComputerBlockEntity(BlockPos arg2, BlockState arg3) {
        super(ModBlockEntities.SCRIPT_COMPUTER.get(), arg2, arg3);
    }

    private String allCommands = "";
    private boolean loop = false;

    @Override
    public void acceptUpdatePacket(ScriptComputerC2SPacket packet) {
        allCommands = packet.contents();
        loop = packet.loop();

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
}
