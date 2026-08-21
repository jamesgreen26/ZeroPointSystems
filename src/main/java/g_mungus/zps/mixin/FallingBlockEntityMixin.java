package g_mungus.zps.mixin;

import g_mungus.zps.block.ZPSBrushableBlock;
import g_mungus.zps.entity.Siftable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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
 * <p>Also makes falling blocks {@link Siftable}: dropping a suspicious block through a sift's mesh
 * unearths its buried loot and leaves plain sand or gravel still falling.
 */
@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin implements Siftable {

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
     * Sifting a falling suspicious block empties its payload into the sift, then swaps the entity
     * for one carrying the block it brushes into, still falling at the same speed. Anything the
     * sift cannot hold spills onto the ground.
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
            ResourceKey<LootTable> key = ResourceKey.create(
                    Registries.LOOT_TABLE, ResourceLocation.parse(data.getString("LootTable")));
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, self.position())
                    .create(LootContextParamSets.CHEST);
            return level.getServer().reloadableRegistries().getLootTable(key)
                    .getRandomItems(params, data.getLong("LootTableSeed"));
        }

        if (data.contains("item", Tag.TAG_COMPOUND)) {
            return ItemStack.parse(level.registryAccess(), data.getCompound("item"))
                    .map(List::of)
                    .orElseGet(List::of);
        }

        return List.of();
    }
}
