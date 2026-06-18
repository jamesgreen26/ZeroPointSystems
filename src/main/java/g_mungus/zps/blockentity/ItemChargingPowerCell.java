package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

public interface ItemChargingPowerCell {
    int INFINITE_ENERGY = -1;

    IItemHandler getChargeInventory();

    int getMenuEnergyStored();

    int getMenuMaxEnergyStored();

    int getChargeItemEnergyStored();

    int getChargeItemMaxEnergyStored();

    BlockPos getBlockPos();

    Level getLevel();
}
