package g_mungus.zps.block;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented on {@link net.minecraft.world.level.block.piston.PistonMovingBlockEntity} by mixin.
 *
 * <p>Vanilla's moving-piston block entity carries only the moved {@code BlockState}, so any block
 * entity attached to a pushed block is destroyed by the move. This lets us ferry a serialised
 * payload across the two ticks a piston stroke takes.
 *
 * <p>A duck interface rather than a plain {@code @Unique} field because the restore hook lives in
 * the <em>static</em> {@code PistonMovingBlockEntity#tick}, and the capture hook lives in a
 * different mixin class entirely.
 */
public interface MovedBlockEntityHolder {

    @Nullable
    CompoundTag zps$getMovedBlockEntityTag();

    void zps$setMovedBlockEntityTag(@Nullable CompoundTag tag);
}
