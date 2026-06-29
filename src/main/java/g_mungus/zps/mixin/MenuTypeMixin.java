package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;

import g_mungus.zps.contraption.ContraptionMenuContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Brackets client-side menu construction from server data so {@link ContraptionMenuContext} can scope
 * its block-entity redirect to exactly the menu factory call (try/finally, exception-safe). This is
 * the universal funnel for every buffer-based menu, so it works for any menu-backed block on a
 * contraption without touching the block or menu. See {@link ContraptionMenuContext}.
 */
@Mixin(MenuType.class)
public class MenuTypeMixin {

	@WrapMethod(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;")
	private AbstractContainerMenu zps$bracketMenuConstruction(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData, Operation<?> original) {
		ContraptionMenuContext.beginConstruction();
		try {
			return (AbstractContainerMenu) original.call(windowId, playerInv, extraData);
		} finally {
			ContraptionMenuContext.endConstruction();
		}
	}
}
