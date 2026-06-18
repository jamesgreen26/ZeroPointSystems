package g_mungus.zps.blockentity;

import g_mungus.zps.networking.RequestHudInfoC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import g_mungus.zps.util.HudInfoProvider;
import g_mungus.zps.util.NumberFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public interface EnergyStorageBE extends HudInfoProvider<Integer> {
    long HUD_REQUEST_INTERVAL_TICKS = 10;
    int INFINITE_FE_INFO = -1;

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
        ZPSGamePackets.sendToServer(new RequestHudInfoC2SPacket(self.getBlockPos()));
    }

    @Override
    default void addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (this instanceof BlockEntity be) {
            requestHudInfo(be);

            String spacing = " ";
            tooltip.add(Component.literal(spacing)
                    .append(Component.literal("   Energy Storage").withStyle(ChatFormatting.WHITE)));

            tooltip.add(Component.literal(spacing)
                    .append(Component.literal("Stored:").withStyle(ChatFormatting.GRAY)));

            int info = getInfo();
            String valueText = info == INFINITE_FE_INFO ? "∞ FE" : NumberFormatter.formatInt(info) + " FE";
            tooltip.add(Component.literal(spacing)
                    .append(Component.literal(valueText).withStyle(ChatFormatting.AQUA)));
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
