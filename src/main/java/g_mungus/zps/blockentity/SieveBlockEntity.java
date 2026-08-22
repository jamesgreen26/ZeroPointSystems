package g_mungus.zps.blockentity;

import g_mungus.zps.menu.SieveMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the sieve's contents. Five slots, laid out like a hopper's; no processing yet.
 */
public class SieveBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 5;

    private final ItemStackHandler inventory = new SieveInventory();

    public SieveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIEVE.get(), pos, state);
    }

    /** Capability view for hoppers and pipes; the same handler the GUI edits. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return inventory;
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.sieve");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player) {
        return new SieveMenu(containerId, inventory, this);
    }

    private class SieveInventory extends ItemStackHandler {
        private SieveInventory() {
            super(SLOT_COUNT);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }
}
