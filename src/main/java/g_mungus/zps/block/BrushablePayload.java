package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Carries a {@link BrushableBlockEntity}'s buried loot across moves that would otherwise drop the
 * block entity: falling, piston strokes and the Beam Collector.
 *
 * <p>The behaviour that makes every {@code BrushableBlock} survive those moves lives in
 * {@code g_mungus.zps.mixin.BrushableBlockMixin}. It patches the vanilla class in place rather than
 * swapping in a subclass, because NeoForge's {@code BlockEntityTypeAddBlocksEvent} requires added
 * blocks to derive from the existing valid blocks' common superclass — a subclass on the vanilla
 * blocks would reject every other mod's suspicious block.
 */
public final class BrushablePayload {

    private BrushablePayload() {
    }

    /** Serialises a brushable block entity's payload, or returns null if there is nothing to carry. */
    @Nullable
    public static CompoundTag snapshot(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable
                ? brushable.saveWithoutMetadata(level.registryAccess())
                : null;
    }

    /**
     * Applies a payload from {@link #snapshot}, mirroring the sequence {@code FallingBlockEntity}
     * uses when a falling block lands on top of a block entity.
     */
    public static void restore(Level level, BlockPos pos, @Nullable CompoundTag payload) {
        if (payload == null || level.isClientSide) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BrushableBlockEntity)) {
            return;
        }

        CompoundTag merged = blockEntity.saveWithoutMetadata(level.registryAccess());
        for (String key : payload.getAllKeys()) {
            merged.put(key, payload.get(key).copy());
        }

        try {
            blockEntity.loadWithComponents(merged, level.registryAccess());
        } catch (Exception e) {
            return;
        }

        blockEntity.setChanged();
    }
}
