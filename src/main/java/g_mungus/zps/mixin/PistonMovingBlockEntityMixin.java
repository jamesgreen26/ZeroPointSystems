package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.zps.block.MovedBlockEntityHolder;
import g_mungus.zps.block.ZPSBrushableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries a {@link ZPSBrushableBlock}'s buried loot across the two ticks a piston stroke takes.
 *
 * <p>Vanilla's moving-piston block entity holds only the moved {@code BlockState}, so without this
 * the payload captured by {@code PistonBaseBlockMixin} would have nowhere to live.
 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin extends BlockEntity implements MovedBlockEntityHolder {

    @Unique
    private static final String ZPS_MOVED_BE_KEY = "zps:MovedBlockEntityData";

    @Unique
    @Nullable
    private CompoundTag zps$movedBlockEntityTag;

    /** Never called; present only to satisfy javac. Mixin discards it. */
    private PistonMovingBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    @Nullable
    public CompoundTag zps$getMovedBlockEntityTag() {
        return this.zps$movedBlockEntityTag;
    }

    @Override
    public void zps$setMovedBlockEntityTag(@Nullable CompoundTag tag) {
        this.zps$movedBlockEntityTag = tag;
    }

    /** Persist across a chunk unload or world save landing inside the stroke. */
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void zps$saveMovedLoot(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.zps$movedBlockEntityTag != null) {
            tag.put(ZPS_MOVED_BE_KEY, this.zps$movedBlockEntityTag.copy());
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void zps$loadMovedLoot(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.zps$movedBlockEntityTag = tag.contains(ZPS_MOVED_BE_KEY, Tag.TAG_COMPOUND)
                ? tag.getCompound(ZPS_MOVED_BE_KEY).copy()
                : null;
    }

    /**
     * {@code getUpdateTag} is {@code return this.saveCustomOnly(provider)}, so anything written in
     * {@code saveAdditional} is broadcast to every client watching the chunk. Strip the payload —
     * vanilla's own {@code BrushableBlockEntity#getUpdateTag} likewise never sends the loot table.
     */
    @ModifyReturnValue(method = "getUpdateTag", at = @At("RETURN"))
    private CompoundTag zps$stripMovedLootFromUpdateTag(CompoundTag tag) {
        tag.remove(ZPS_MOVED_BE_KEY);
        return tag;
    }

    @Inject(
            method = "finalTick",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private void zps$restoreOnFinalTick(CallbackInfo ci) {
        if (this.zps$movedBlockEntityTag == null || this.level == null) {
            return;
        }
        ZPSBrushableBlock.restore(this.level, this.worldPosition, this.zps$movedBlockEntityTag);
        this.zps$movedBlockEntityTag = null;
    }

    /**
     * Matches both {@code setBlock} sites in the static tick. Reaches the field through the duck
     * interface because the method is static. Clearing the field afterwards prevents a double-apply
     * when a second piston action interrupts a move and {@code finalTick} runs after the block has
     * already been placed.
     */
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private static void zps$restoreOnTick(Level level, BlockPos pos, BlockState state,
                                          PistonMovingBlockEntity blockEntity, CallbackInfo ci) {
        if (level.isClientSide || !(blockEntity instanceof MovedBlockEntityHolder holder)) {
            return;
        }
        CompoundTag payload = holder.zps$getMovedBlockEntityTag();
        if (payload == null) {
            return;
        }
        ZPSBrushableBlock.restore(level, pos, payload);
        holder.zps$setMovedBlockEntityTag(null);
    }
}
