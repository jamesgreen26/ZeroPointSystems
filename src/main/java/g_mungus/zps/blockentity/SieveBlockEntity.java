package g_mungus.zps.blockentity;

import g_mungus.zps.menu.SieveMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the sieve's contents. Five slots, laid out like a hopper's; no processing yet.
 */
public class SieveBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 5;

    private final ItemStackHandler inventory = new SieveInventory();
    /** Capability view for hoppers and pipes; the same handler the GUI edits. */
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> inventory);

    public SieveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIEVE.get(), pos, state);
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
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
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

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
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
