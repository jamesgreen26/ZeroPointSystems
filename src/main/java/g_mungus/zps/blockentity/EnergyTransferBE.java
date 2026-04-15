package g_mungus.zps.blockentity;

import g_mungus.zps.networking.RequestHudInfoC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public interface EnergyTransferBE extends HudInfoProvider<Integer> {
    long HUD_REQUEST_INTERVAL_TICKS = 10;

    void setLastHudRefreshTick(long ticks);

    long getLastHudRefreshTick();

    private void requestHudInfo(BlockEntity self) {
        if (self.getLevel() == null || !self.getLevel().isClientSide) {
            return;
        }

        long gameTime = self.getLevel().getGameTime();
        long lastRefreshTick = getLastHudRefreshTick();
        if (lastRefreshTick != Long.MIN_VALUE && gameTime - lastRefreshTick < HUD_REQUEST_INTERVAL_TICKS) {
            return;
        }

        setLastHudRefreshTick(gameTime);
        ZPSGamePackets.INSTANCE.sendToServer(new RequestHudInfoC2SPacket(self.getBlockPos()));
    }

    @Override
    default void addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (this instanceof BlockEntity be) {
            requestHudInfo(be);

            String spacing = " ";
            tooltip.add(Component.literal(spacing)
                    .append(Component.literal("   Transformer Info").withStyle(ChatFormatting.WHITE)));

            tooltip.add(Component.literal(spacing)
                    .append(Component.literal("Transfer Rate:").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal(spacing).append(" ")
                    .append(formatInt(getInfo())).append(" FE/t").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    default CompoundTag writeInfo(Integer info) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("info", info);
        return tag;
    }

    @Override
    default Integer readInfo(CompoundTag tag) {
        return tag.getInt("info");
    }
}
