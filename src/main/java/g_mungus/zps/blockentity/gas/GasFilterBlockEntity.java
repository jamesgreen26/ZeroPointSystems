package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.GasFilterBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the {@link GasFilterBlock}'s list of blocked gases. The block reads it when it proposes
 * its edges, so changing it renegotiates every face.
 */
public class GasFilterBlockEntity extends GasNodeBlockEntity {

    private static final String FILTER_TAG = "Filter";

    private GasFilter filter = GasFilter.PASS_ALL;

    public GasFilterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_FILTER.get(), pos, state);
    }

    /** Which gases the block holds back. Valid on both sides. */
    public GasFilter getFilter() {
        return filter;
    }

    /** What the screen sends. Server only; a filter naming too many gases is ignored. */
    public void setFilter(GasFilter newFilter) {
        if (!(level instanceof ServerLevel serverLevel)
                || newFilter.blocked().size() > GasFilter.MAX_GASES
                || newFilter.equals(filter)) {
            return;
        }
        filter = newFilter;
        setChanged();
        BlockState state = getBlockState();
        serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        GasEdgeNegotiator.updateConnections(serverLevel, worldPosition);
    }

    // --- persistence and sync ---------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(FILTER_TAG, filter.save(new CompoundTag()));
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        filter = GasFilter.load(tag.getCompound(FILTER_TAG));
    }

    /** The filter goes to clients for the screen; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(FILTER_TAG, filter.save(new CompoundTag()));
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        filter = GasFilter.load(tag.getCompound(FILTER_TAG));
    }
}
