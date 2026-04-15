package g_mungus.zps.mixin.create;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(value = HudInfoProvider.class, remap = false)
public interface GoggleInfoExtension extends IHaveGoggleInformation {

    @Override
    default boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ((HudInfoProvider<?>) this).addToTooltip(tooltip, isPlayerSneaking);
        return false;
    }
}
