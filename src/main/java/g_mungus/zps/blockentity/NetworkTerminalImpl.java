package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NetworkTerminalImpl extends BlockEntity implements NetworkTerminal {

    public NetworkTerminalImpl(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private final Map<Integer, List<NetworkNode>> terminals = new ConcurrentHashMap<>();

    @Override
    public BlockPos getWorldPos() {
        return this.worldPosition;
    }

    @Override
    public Map<Integer, List<NetworkNode>> getTerminalHolder() {
        return terminals;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadNetwork(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveNetwork(tag);
    }
}
