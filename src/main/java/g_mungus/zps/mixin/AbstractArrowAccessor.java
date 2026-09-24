package g_mungus.zps.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches whether an arrow is stuck in a block.
 *
 * <p>A stuck arrow ignores its velocity until the block it is in goes away, so a Tractor Beam that wants to pull
 * one loose has to unstick it first, the way it pulls an item out of the block it is buried in.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {

    @Accessor("inGround")
    boolean zps$isInGround();

    @Accessor("inGround")
    void zps$setInGround(boolean inGround);

    /** What a player would get picking the arrow up; protected in this version. */
    @Invoker("getPickupItem")
    ItemStack zps$getPickupItem();
}
