package g_mungus.zps.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes the recipe book's private tab state so the assembler screen can move the tabs to the right edge. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor
    List<RecipeBookTabButton> getTabButtons();

    @Accessor
    RecipeBookTabButton getSelectedTab();

    @Accessor
    void setSelectedTab(RecipeBookTabButton tab);
}
