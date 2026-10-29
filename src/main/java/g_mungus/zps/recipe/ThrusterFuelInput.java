package g_mungus.zps.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.GasType;

/** A gas reaching a thruster, presented to {@link ThrusterFuelRecipe} for matching. Holds no items. */
public record ThrusterFuelInput(GasType gas) implements Container {

    @Override
    public @NotNull ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getContainerSize() {
        return 0;
    }

    /** Never empty: there are no items, but the gas is the input. */
    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return false;
    }

    @Override
    public void clearContent() {
    }
}
