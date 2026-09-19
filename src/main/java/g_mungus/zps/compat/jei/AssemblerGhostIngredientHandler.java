package g_mungus.zps.compat.jei;

import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.client.screens.AssemblerScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets items be dragged out of JEI straight onto the assembler's pattern grid (as Create's list filter does),
 * so a template can be built without owning the ingredients. Each of the 25 ghost cells is a drop target;
 * dropping stamps that cell exactly as clicking it with the item in hand would.
 */
public class AssemblerGhostIngredientHandler implements IGhostIngredientHandler<AssemblerScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(AssemblerScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        // Only item ingredients can be stamped; fluids and other JEI types have no pattern representation.
        if (ingredient.getItemStack().isEmpty()) {
            return List.of();
        }
        List<Target<I>> targets = new ArrayList<>(AssemblerBlockEntity.PATTERN_SLOTS);
        for (int i = 0; i < AssemblerBlockEntity.PATTERN_SLOTS; i++) {
            // Ghost cells are the menu's first PATTERN_SLOTS slots, in row-major order.
            targets.add(new PatternCellTarget<>(screen, i));
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    private record PatternCellTarget<I>(AssemblerScreen screen, int patternIndex) implements Target<I> {
        @Override
        public Rect2i getArea() {
            Slot slot = screen.getMenu().slots.get(patternIndex);
            return new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                screen.stampGhostFromDrag(patternIndex, stack);
            }
        }
    }
}
