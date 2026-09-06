package g_mungus.zps.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Boilerplate for a {@link MultiblockPart} block entity: controller position, structure size, deferred
 * connectivity updates, position-change detection (for moved blocks) and (de)serialisation of all of the
 * above for both disk and client sync.
 * <p>
 * Subclasses call {@link #tickMultiblock()} from their server tick, {@link #requestConnectivityUpdate()} when
 * placed, and override the contents hooks of {@link MultiblockPart} plus {@link #notifyMultiUpdated()}.
 */
public abstract class MultiblockBlockEntity extends BlockEntity implements MultiblockPart {
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_LAST_KNOWN_POS = "LastKnownPos";
    private static final String TAG_WIDTH = "Size";
    private static final String TAG_HEIGHT = "Height";
    private static final String TAG_UNINITIALIZED = "Uninitialized";

    /** NBT keys that describe where a block sits in a structure; meaningless once the block is an item. */
    public static final String[] STRUCTURE_TAGS = {TAG_CONTROLLER, TAG_LAST_KNOWN_POS, TAG_WIDTH, TAG_HEIGHT,
            TAG_UNINITIALIZED};

    @Nullable
    protected BlockPos controller;
    @Nullable
    protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected int width = 1;
    protected int height = 1;

    protected MultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- size limits ------------------------------------------------------------------------------

    @Override
    public abstract int getMaxWidth();

    /** Longest allowed extent along the main connection axis. */
    public abstract int getMaxHeight();

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == getMainConnectionAxis() ? getMaxHeight() : getMaxWidth();
    }

    // --- ticking ----------------------------------------------------------------------------------

    /** Call from the server tick before any other work. */
    protected void tickMultiblock() {
        if (lastKnownPos == null) {
            lastKnownPos = getBlockPos();
        } else if (!lastKnownPos.equals(worldPosition)) {
            onPositionChanged();
            return;
        }
        if (updateConnectivity) {
            updateConnectivity();
        }
    }

    /** Ask for a (re)formation on the next server tick, e.g. right after being placed. */
    public void requestConnectivityUpdate() {
        updateConnectivity = true;
        setChanged();
    }

    public void updateConnectivity() {
        updateConnectivity = false;
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!isController()) {
            return;
        }
        ConnectivityHandler.formMulti(this);
    }

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    // --- controller -------------------------------------------------------------------------------

    @Override
    public boolean isController() {
        return controller == null || worldPosition.equals(controller);
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity & MultiblockPart> T getControllerBE() {
        if (isController() || level == null) {
            return (T) this;
        }
        BlockEntity blockEntity = level.getBlockEntity(controller);
        if (blockEntity != null && blockEntity.getType() == getType() && blockEntity instanceof MultiblockPart) {
            return (T) blockEntity;
        }
        return null;
    }

    @Override
    @Nullable
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    public void setController(BlockPos controller) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (controller.equals(this.controller)) {
            return;
        }
        this.controller = controller;
        onControllerChanged();
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level == null || level.isClientSide()) {
            return;
        }
        updateConnectivity = true;
        controller = null;
        width = 1;
        height = 1;
        onControllerRemoved(keepContents);
        onControllerChanged();
        notifyMultiUpdated();
    }

    /** Hook: this part just became standalone again (size fields are already reset). */
    protected void onControllerRemoved(boolean keepContents) {
    }

    private void onControllerChanged() {
        invalidateCapabilities();
        setChanged();
        sendBlockEntityUpdate();
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    /** Number of blocks in the structure this part heads (1 when standalone or not the controller). */
    public int getStructureSize() {
        return width * width * height;
    }

    /** Offset of this part from its controller, in blocks. */
    public BlockPos getOffsetInStructure() {
        return worldPosition.subtract(getController());
    }

    // --- sync -------------------------------------------------------------------------------------

    protected void sendBlockEntityUpdate() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        writeMultiblock(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        readMultiblock(tag);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeMultiblock(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    protected void writeMultiblock(CompoundTag tag) {
        if (updateConnectivity) {
            tag.putBoolean(TAG_UNINITIALIZED, true);
        }
        if (lastKnownPos != null) {
            tag.put(TAG_LAST_KNOWN_POS, NbtUtils.writeBlockPos(lastKnownPos));
        }
        if (!isController()) {
            tag.put(TAG_CONTROLLER, NbtUtils.writeBlockPos(controller));
        }
        tag.putInt(TAG_WIDTH, width);
        tag.putInt(TAG_HEIGHT, height);
    }

    protected void readMultiblock(CompoundTag tag) {
        updateConnectivity = tag.contains(TAG_UNINITIALIZED);
        lastKnownPos = NbtUtils.readBlockPos(tag, TAG_LAST_KNOWN_POS).orElse(null);
        controller = NbtUtils.readBlockPos(tag, TAG_CONTROLLER).orElse(null);
        width = Math.max(1, tag.getInt(TAG_WIDTH));
        height = Math.max(1, tag.getInt(TAG_HEIGHT));
    }
}
