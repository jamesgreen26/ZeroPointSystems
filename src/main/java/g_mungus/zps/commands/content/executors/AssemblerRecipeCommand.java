package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.commands.content.AssemblerRecipeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Backs the {@code set_recipe} script command: fills the target Assembler's pattern from a recipe id. */
public final class AssemblerRecipeCommand {

    private AssemblerRecipeCommand() {
    }

    /**
     * Resolves the Assembler at {@code pos} and fills its pattern from the recipe {@code idString}, clearing
     * any previous pattern. Returns {@code 1} on success, or {@code 0} (leaving the pattern untouched) if the
     * block is not an Assembler, the id is malformed, or the recipe is not one the Assembler can fulfill.
     */
    public static int setRecipe(ServerLevel level, BlockPos pos, String idString) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AssemblerBlockEntity assembler)) {
            return 0;
        }
        ResourceLocation id = ResourceLocation.tryParse(idString);
        if (id == null) {
            return 0;
        }
        Recipe<?> recipe = AssemblerRecipeSupport.resolveFulfillable(level, id);
        if (recipe == null) {
            return 0;
        }
        List<Ingredient> grid = AssemblerRecipeSupport.toGrid25(recipe);
        if (grid == null) {
            return 0;
        }
        assembler.setPattern(grid);
        return 1;
    }
}
