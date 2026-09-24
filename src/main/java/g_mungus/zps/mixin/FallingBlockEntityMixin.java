package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.zps.block.ZPSBrushableBlock;
import g_mungus.zps.entity.Siftable;
import g_mungus.zps.entity.TractorCargo;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * When a falling {@link ZPSBrushableBlock} cannot be placed and drops as an item instead, drop what
 * it brushes into (sand / gravel) rather than the suspicious block.
 *
 * <p>A suspicious sand item is a misleading thing to hand back: the payload is gone by that point,
 * so the item promises something it can no longer deliver. The plain block is the honest drop.
 *
 * <p>Covers all three drop sites in {@code tick} — placement refused, destination not replaceable,
 * and the fell-too-long timeout — since the payload is lost in every one of them.
 *
 * <p>Also makes falling blocks {@link Siftable}: dropping a suspicious block through a sieve's mesh
 * unearths its buried loot and leaves plain sand or gravel still falling.
 */
@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin implements Siftable, TractorCargo {

    /** The game time a Tractor Beam last said it was carrying this block, or the minimum when none is. */
    @Unique
    private long zps$carriedAt = Long.MIN_VALUE;

    @Override
    public void zps$carriedByBeam() {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        self.noPhysics = true;
        zps$carriedAt = self.level().getGameTime();
    }

    /**
     * Collision comes back once the beam has gone a tick without renewing its hold. Entities tick before block
     * entities, so a hold renewed last tick is the normal state of a block in flight, not a lapsed one.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void zps$letGoOfLapsedCargo(CallbackInfo ci) {
        if (zps$carriedAt == Long.MIN_VALUE) {
            return;
        }
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (self.level().getGameTime() - zps$carriedAt > 1) {
            self.noPhysics = false;
            zps$carriedAt = Long.MIN_VALUE;
        }
    }

    /**
     * A block in a beam does not land. Landing is what {@code tick} does when the block is on the ground, and a
     * carried block never should be: it is weightless and collides with nothing. But "on the ground" is a flag
     * that others may set. Both ship mods run collision of their own for entities near a ship, and say a falling
     * block is standing on a deck it is only passing, at which point it turns back into a block on the spot,
     * right where it was pulled loose. So while it is carried, the answer is no, whoever asks.
     *
     * <p>Checked after {@link #zps$letGoOfLapsedCargo}, which runs first in the same tick, so a block the beam has
     * let go of lands as usual.
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;onGround()Z"
            )
    )
    private boolean zps$carriedCargoNeverLands(boolean onGround) {
        return onGround && zps$carriedAt == Long.MIN_VALUE;
    }

    /**
     * The other way a falling block places itself: concrete powder sets the moment it touches water, in mid-air.
     * A beam carrying powder across a pond is carrying powder, so that waits until it is let go too.
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;canBeHydrated("
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;)Z"
            ),
            require = 2
    )
    private boolean zps$carriedPowderStaysPowder(boolean canBeHydrated) {
        return canBeHydrated && zps$carriedAt == Long.MIN_VALUE;
    }

    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;spawnAtLocation("
                            + "Lnet/minecraft/world/level/ItemLike;)"
                            + "Lnet/minecraft/world/entity/item/ItemEntity;"
            ),
            require = 3
    )
    private ItemLike zps$dropTurnsIntoInstead(ItemLike dropped) {
        return dropped instanceof ZPSBrushableBlock brushable ? brushable.getTurnsInto() : dropped;
    }

    /**
     * Sieveing a falling suspicious block empties its payload into the sieve, then swaps the entity
     * for one carrying the block it brushes into, still falling at the same speed. Anything the
     * sieve cannot hold spills onto the ground.
     */
    @Override
    public void sift(IItemHandler inventory) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!(self.getBlockState().getBlock() instanceof ZPSBrushableBlock brushable)
                || !(self.level() instanceof ServerLevel level)) {
            return;
        }

        for (ItemStack stack : zps$buriedLoot(level, self)) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, stack, false);
            if (!remainder.isEmpty()) {
                Containers.dropItemStack(level, self.getX(), self.getY(), self.getZ(), remainder);
            }
        }

        FallingBlockEntity replacement = FallingBlockEntityInvoker.zps$create(
                level, self.getX(), self.getY(), self.getZ(), brushable.getTurnsInto().defaultBlockState());
        replacement.setDeltaMovement(self.getDeltaMovement());
        // Carried over so the replacement inherits the fell-too-long timeout rather than restarting it.
        replacement.time = self.time;
        level.addFreshEntity(replacement);
        self.discard();
    }

    /**
     * The buried payload as items. A brushable block entity stores either an unrolled loot table
     * reference or, once brushing has already resolved it, the item itself; {@code blockData} is
     * that block entity's NBT, snapshotted by {@link ZPSBrushableBlock#snapshot}.
     */
    private static List<ItemStack> zps$buriedLoot(ServerLevel level, FallingBlockEntity self) {
        CompoundTag data = self.blockData;
        if (data == null) {
            return List.of();
        }

        if (data.contains("LootTable", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString("LootTable"));
            if (id == null) {
                return List.of();
            }
            LootTable table = level.getServer().getLootData().getLootTable(id);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, self.position())
                    .create(LootContextParamSets.CHEST);
            return table.getRandomItems(params, data.getLong("LootTableSeed"));
        }

        if (data.contains("item", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of(data.getCompound("item"));
            return stack.isEmpty() ? List.of() : List.of(stack);
        }

        return List.of();
    }
}
