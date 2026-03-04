package g_mungus.zps.client.ponder;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.CreateCompat;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ZPSPonderPlugin implements PonderPlugin {

    @Override
    public @NotNull String getModId() {
        return ZPSMod.MOD_ID;
    }

    @Override
    public void registerScenes(@NotNull PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ZPSPonders.register(helper);
    }

    @Override
    public void registerTags(@NotNull PonderTagRegistrationHelper<ResourceLocation> helper) {
        ZPSPonderTags.register(helper);
        if (Compat.isCreateLoaded()) {
            CreateCompat.registerPonderTagEntries(helper);
        }
    }

    public static void registerPlugin() {
        PonderIndex.addPlugin(new ZPSPonderPlugin());
    }
}
