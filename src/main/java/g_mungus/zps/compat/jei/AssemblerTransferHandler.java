package g_mungus.zps.compat.jei;

import g_mungus.zps.commands.content.AssemblerRecipeSupport;
import g_mungus.zps.menu.AssemblerMenu;
import g_mungus.zps.menu.ModMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Wires JEI's "+" button to the Assembler. The pattern grid holds no real items, so nothing is moved:
 * we send the vanilla place-recipe packet and let {@link AssemblerMenu#handlePlacement} stamp the ghost
 * cells, exactly as the embedded recipe book and the {@code set_recipe} command do.
 *
 * <p>Generic over the recipe class so one handler serves both the 5x5 category and vanilla crafting.
 */
public class AssemblerTransferHandler<R extends Recipe<?>> implements IRecipeTransferHandler<AssemblerMenu, RecipeHolder<R>> {
    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<RecipeHolder<R>> recipeType;

    public AssemblerTransferHandler(IRecipeTransferHandlerHelper helper, RecipeType<RecipeHolder<R>> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override
    public Class<? extends AssemblerMenu> getContainerClass() {
        return AssemblerMenu.class;
    }

    @Override
    public Optional<MenuType<AssemblerMenu>> getMenuType() {
        return Optional.of(ModMenus.ASSEMBLER.get());
    }

    @Override
    public RecipeType<RecipeHolder<R>> getRecipeType() {
        return recipeType;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(AssemblerMenu container, RecipeHolder<R> recipe,
                                                        IRecipeSlotsView recipeSlots, Player player,
                                                        boolean maxTransfer, boolean doTransfer) {
        // Rules out dynamic recipes (no fixed ingredients) and anything too big for the 5x5 grid.
        if (!AssemblerRecipeSupport.isFulfillable(recipe.value())) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.zps.jei.transfer_unsupported"));
        }
        if (doTransfer) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return helper.createInternalError();
            }
            connection.send(new ServerboundPlaceRecipePacket(container.containerId, recipe, maxTransfer));
        }
        return null;
    }
}
