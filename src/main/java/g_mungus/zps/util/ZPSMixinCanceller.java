package g_mungus.zps.util;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;

/** Drops the mixins that make a ZPS interface extend another mod's, for each of those mods not installed. */
public class ZPSMixinCanceller implements MixinCanceller {
    private static final Map<String, String> REQUIRED_MOD = Map.of(
            "g_mungus.zps.mixin.create.GoggleInfoExtension", "create",
            "g_mungus.zps.mixin.vs.PhysicsListenerExtension", "valkyrienskies");

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        String mod = REQUIRED_MOD.get(mixinClassName);
        return mod != null && LoadingModList.get().getModFileById(mod) == null;
    }
}
