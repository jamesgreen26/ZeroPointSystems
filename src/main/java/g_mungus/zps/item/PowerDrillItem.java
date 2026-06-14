package g_mungus.zps.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerDrillItem extends DiggerItem {
    private static final String ENERGY_TAG = "Energy";
    private static final int MAX_ENERGY = 128_000;
    private static final int ENERGY_PER_BLOCK = 96;
    private static final int ENERGY_BAR_COLOR = 0x55FFFF;
    private static final float EFFICIENCY_FIVE_SPEED_BONUS = 26.0F;

    public PowerDrillItem(Properties properties) {
        super(1.0F, -2.8F, Tiers.IRON, BlockTags.MINEABLE_WITH_PICKAXE, properties.setNoRepair());
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!hasEnergyForBlock(stack) || !isDrillMineable(state)) {
            return 1.0F;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return Tiers.IRON.getSpeed() + EFFICIENCY_FIVE_SPEED_BONUS;
        }
        return Tiers.IRON.getSpeed();
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return hasEnergyForBlock(stack) && isDrillMineable(state) && TierSortingRegistry.isCorrectTierForDrops(Tiers.IRON, state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F && hasEnergyForBlock(stack) && isDrillMineable(state)) {
            extractEnergy(stack, ENERGY_PER_BLOCK, false);
            if (!hasEnergyForBlock(stack) && entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("LOW POWER").withStyle(ChatFormatting.RED), true);
            }
        }
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getStoredEnergy(stack) / MAX_ENERGY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ENERGY_BAR_COLOR;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.zps.power_drill.energy",
                formatInt(getStoredEnergy(stack)),
                formatInt(MAX_ENERGY)
        ).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EnergyCapabilityProvider(stack);
    }

    private static boolean isDrillMineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static boolean hasEnergyForBlock(ItemStack stack) {
        return getStoredEnergy(stack) >= ENERGY_PER_BLOCK;
    }

    private static int getStoredEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.min(tag.getInt(ENERGY_TAG), MAX_ENERGY);
    }

    private static void setStoredEnergy(ItemStack stack, int energy) {
        stack.getOrCreateTag().putInt(ENERGY_TAG, Math.max(0, Math.min(energy, MAX_ENERGY)));
    }

    private static int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        int extracted = Math.min(getStoredEnergy(stack), Math.max(0, maxExtract));
        if (!simulate && extracted > 0) {
            setStoredEnergy(stack, getStoredEnergy(stack) - extracted);
        }
        return extracted;
    }

    private static String formatInt(int n) {
        if (n > 1_000_000) {
            return Math.round((double) n / 100_000d) / 10d + "M";
        }
        if (n > 10_000) {
            return Math.round((double) n / 100d) / 10d + "K";
        }
        return n + "";
    }

    private static class EnergyCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
        private final StackEnergyStorage energyStorage;
        private final LazyOptional<IEnergyStorage> energy;

        private EnergyCapabilityProvider(ItemStack stack) {
            this.energyStorage = new StackEnergyStorage(stack);
            this.energy = LazyOptional.of(() -> energyStorage);
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(ENERGY_TAG, energyStorage.getEnergyStored());
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            setStoredEnergy(energyStorage.stack, tag.getInt(ENERGY_TAG));
        }
    }

    private static class StackEnergyStorage implements IEnergyStorage {
        private final ItemStack stack;

        private StackEnergyStorage(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = Math.min(MAX_ENERGY - getEnergyStored(), Math.max(0, maxReceive));
            if (!simulate && received > 0) {
                setStoredEnergy(stack, getEnergyStored() + received);
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return PowerDrillItem.extractEnergy(stack, maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return getStoredEnergy(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return MAX_ENERGY;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
