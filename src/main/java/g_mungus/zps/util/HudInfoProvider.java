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
}
