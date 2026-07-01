package g_mungus.zps.client.recipebook;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

public final class RollingMillRecipeBookCategoryParameters {
    private RollingMillRecipeBookCategoryParameters() {
    }

    public static Object getSearchCategoryIcon(int index, Class<?> type) {
        return type.cast((Supplier<List<ItemStack>>) () -> List.of(new ItemStack(Items.COMPASS)));
    }

    public static Object getRollingMillCategoryIcon(int index, Class<?> type) {
        return type.cast((Supplier<List<ItemStack>>) () -> List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_NUGGET)));
    }
}
