package g_mungus.zps.mixin;

import g_mungus.zps.block.ZPSBrushableBlock;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * When a falling {@link ZPSBrushableBlock} cannot be placed and drops as an item instead, drop what
 * it brushes into (sand / gravel) rather than the suspicious block.
 *
 * <p>A suspicious sand item is a misleading thing to hand back: the payload is gone by that point,
 * so the item promises something it can no longer deliver. The plain block is the honest drop.
 *
 * <p>Covers all three drop sites in {@code tick} — placement refused, destination not replaceable,
 * and the fell-too-long timeout — since the payload is lost in every one of them.
 */
@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {

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
}
