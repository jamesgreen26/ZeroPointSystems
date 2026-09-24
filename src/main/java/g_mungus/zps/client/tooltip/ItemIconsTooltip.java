package g_mungus.zps.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Tooltip payload drawn as a grid of item icons. Rendered by {@link ClientItemIconsTooltip}. */
public record ItemIconsTooltip(List<ItemStack> items) implements TooltipComponent {
    public ItemIconsTooltip {
        items = List.copyOf(items);
    }
}
