package g_mungus.zps.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface HudInfoProvider<T> {

    void provideInfo(T info);
    T getInfo();

    void addToTooltip(List<Component> tooltip, boolean isPlayerSneaking);

    CompoundTag writeInfo(T info);

    T readInfo(CompoundTag tag);

    default String formatInt(int n) {
        if(n > 10_000_000)
            return Math.round((double)n/100_000d)/10d + "M";
        if(n > 10_000)
            return Math.round((double)n/100d)/10d + "K";
        return n + "";
    }
}
