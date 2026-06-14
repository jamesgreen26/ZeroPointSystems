package g_mungus.zps.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.client.renderer.PowerDrillItemRenderer;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class PowerDrillItem extends DiggerItem implements CustomArmPoseItem {
    private static final String ENERGY_TAG = "Energy";
    private static final String LAST_POWERED_USE_TICK_TAG = "LastPoweredUseTick";
    private static final String DRILL_ID_TAG = "DrillId";
    public static final int MAX_ENERGY = 128_000;
    private static final int ENERGY_PER_BLOCK = 96;
    private static final int ENERGY_BAR_COLOR = 0x55FFFF;
    private static final float EFFICIENCY_FIVE_SPEED_BONUS = 26.0F;

    public PowerDrillItem(Properties properties) {
        super(1.0F, -2.8F, Tiers.IRON, BlockTags.MINEABLE_WITH_PICKAXE, properties.setNoRepair());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final PowerDrillItemRenderer renderer = new PowerDrillItemRenderer();

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                                   float partialTick, float equipProcess, float swingProcess) {
                int handSide = arm == HumanoidArm.RIGHT ? 1 : -1;
                poseStack.translate(handSide * 0.36F, -0.54F + equipProcess * -0.6F, -0.98F);
                poseStack.mulPose(Axis.YP.rotationDegrees(handSide * 18.0F));
                float upwardTilt = renderer.getBoostProgress(itemInHand, Minecraft.getInstance()) * 20.0F;
                poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F + upwardTilt));
                poseStack.mulPose(Axis.ZP.rotationDegrees(handSide * -8.0F));
                return true;
            }
        });
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack.getItem() instanceof PowerDrillItem && newStack.getItem() instanceof PowerDrillItem) {
            return getOrCreateDrillId(oldStack) != getOrCreateDrillId(newStack);
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        getOrCreateDrillId(stack);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
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
        if (state.getDestroySpeed(level, pos) != 0.0F && hasEnergyForBlock(stack) && isDrillMineable(state)) {
            markPoweredUse(stack, level);
            if (!level.isClientSide) {
                extractEnergy(stack, ENERGY_PER_BLOCK, false);
                if (!hasEnergyForBlock(stack) && entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("LOW POWER").withStyle(ChatFormatting.RED), true);
                }
            }
        }
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (hasEnergyForBlock(stack)) {
            markPoweredUse(stack, attacker.level());
        }
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
                NumberFormatter.formatInt(getStoredEnergy(stack)),
                NumberFormatter.formatInt(MAX_ENERGY)
        ).withStyle(style -> style.withColor(ShiftTooltipHandler.BASE_COLOR)));
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

    public static boolean hasEnergyForBlock(ItemStack stack) {
        return getStoredEnergy(stack) >= ENERGY_PER_BLOCK;
    }

    public static long getLastPoweredUseTick(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Long.MIN_VALUE : tag.getLong(LAST_POWERED_USE_TICK_TAG);
    }

    public static long getOrCreateDrillId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(DRILL_ID_TAG)) {
            tag.putLong(DRILL_ID_TAG, generateDrillId());
        }
        return tag.getLong(DRILL_ID_TAG);
    }

    private static int getStoredEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.min(tag.getInt(ENERGY_TAG), MAX_ENERGY);
    }

    private static long generateDrillId() {
        long id = ThreadLocalRandom.current().nextLong();
        return id == 0L ? 1L : id;
    }

    private static void markPoweredUse(ItemStack stack, Level level) {
        stack.getOrCreateTag().putLong(LAST_POWERED_USE_TICK_TAG, level.getGameTime());
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

    @Override
    public HumanoidModel.@Nullable ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
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
