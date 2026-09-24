package g_mungus.zps.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that can join with identical neighbours into one {@code width x width x length} structure
 * driven by a single controller (the part at the structure's minimum corner).
 * <p>
 * Structural bookkeeping is handled by {@link ConnectivityHandler}; {@link MultiblockBlockEntity} provides a
 * ready-made implementation of the plumbing. Implementors only need to describe what their contents are
 * through the {@code *Contents} hooks so they can be pooled at the controller and handed back out on split.
 * <p>
 * Adapted from Create's {@code IMultiBlockEntityContainer} (MIT).
 */
public interface MultiblockPart {

    BlockPos getController();

    @Nullable
    <T extends BlockEntity & MultiblockPart> T getControllerBE();

    boolean isController();

    void setController(BlockPos pos);

    void removeController(boolean keepContents);

    @Nullable
    BlockPos getLastKnownPos();

    void preventConnectivityUpdate();

    /** Called whenever this part's controller, width or height changed; refresh block state / caches here. */
    void notifyMultiUpdated();

    // Optional payload shared by every part of a structure (Create used this for tank windows).
    default void setExtraData(@Nullable Object data) {}

    @Nullable
    default Object getExtraData() {
        return null;
    }

    default Object modifyExtraData(Object data) {
        return data;
    }

    // --- structural information ------------------------------------------------------------------

    Direction.Axis getMainConnectionAxis();

    default Direction.Axis getMainAxisOf(BlockEntity be) {
        BlockState state = be.getBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getAxis();
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        return Direction.Axis.Y;
    }

    int getMaxLength(Direction.Axis longAxis, int width);

    int getMaxWidth();

    int getHeight();

    void setHeight(int height);

    int getWidth();

    void setWidth(int width);

    // --- contents ----------------------------------------------------------------------------------

    /** Whether {@code other} (a controller) is allowed to become part of the same structure as this part. */
    default boolean canMergeWith(MultiblockPart other) {
        return true;
    }

    /** Controller only: resize storage so it can hold the contents of {@code blocks} parts. */
    default void setContainerSize(int blocks) {}

    /**
     * Controller only: {@code part} is about to join this structure. Move everything it holds into this
     * controller and leave it empty.
     */
    default void absorbContents(MultiblockPart part) {}

    /**
     * Controller only, called before its structure is dissolved. Keep one part's worth of contents (nothing if
     * this block entity is removed), shrink storage to a single block and return whatever is left over so it can
     * be handed to the other parts through {@link #receiveSplitContents(Object)}.
     */
    @Nullable
    default Object takeSplitContents() {
        return null;
    }

    /**
     * Called on each former part (never the old controller) right after it became standalone, with the payload
     * returned by {@link #takeSplitContents()}. Take a share and return the remainder for the next part.
     */
    @Nullable
    default Object receiveSplitContents(@Nullable Object contents) {
        return contents;
    }
}
