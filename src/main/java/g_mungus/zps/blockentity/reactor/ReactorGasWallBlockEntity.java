package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared state for the gas-carrying reactor wall blocks: the Fuel Injector and the Exhaust Port.
 *
 * <p>Holds the strongest redstone signal reaching the block. The block pushes a fresh reading on
 * every neighbour change; a block that has just loaded reads it on its first tick instead, since
 * asking neighbours for their signal while the chunk is still loading can pull further chunks in.
 * The level is persisted and mirrored to clients, where the face overlay is tinted by it, and on
 * the server it sets the aperture of the block's outer face: see {@code ReactorGasWallBlock}.
 */
public abstract class ReactorGasWallBlockEntity extends GasNodeBlockEntity {

    public static final int MAX_REDSTONE_LEVEL = 15;

    private static final String REDSTONE_TAG = "Redstone";

    private int redstoneLevel;
    private boolean redstoneStale = true;

    protected ReactorGasWallBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Runs the shared per-tick work; subclasses call this from their own server tick. */
    protected void serverTick(ServerLevel serverLevel) {
        if (redstoneStale) {
            refreshRedstoneLevel();
        }
    }

    // --- redstone ---------------------------------------------------------------------------

    /** The strongest redstone signal reaching this block, 0..15. Valid on both sides. */
    public int getRedstoneLevel() {
        return redstoneLevel;
    }

    /** Re-read the signal from the block's neighbours. Server only; no-op elsewhere. */
    public void refreshRedstoneLevel() {
        if (level == null || level.isClientSide()) {
            return;
        }
        redstoneStale = false;
        setRedstoneLevel(level.getBestNeighborSignal(worldPosition));
    }

    private void setRedstoneLevel(int newLevel) {
        newLevel = Math.max(0, Math.min(MAX_REDSTONE_LEVEL, newLevel));
        if (newLevel == redstoneLevel) {
            return;
        }
        redstoneLevel = newLevel;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            // The level sets the outer face's aperture, so the edge there needs rebuilding.
            GasEdgeNegotiator.updateConnections(level, worldPosition);
        }
    }

    // --- persistence and sync ---------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(REDSTONE_TAG, redstoneLevel);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        redstoneLevel = tag.getInt(REDSTONE_TAG);
        // Neighbours may have changed while this block was unloaded; confirm on the first tick.
        redstoneStale = true;
    }

    /** Only the redstone level goes to clients; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(REDSTONE_TAG, redstoneLevel);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        redstoneLevel = tag.getInt(REDSTONE_TAG);
    }
}
