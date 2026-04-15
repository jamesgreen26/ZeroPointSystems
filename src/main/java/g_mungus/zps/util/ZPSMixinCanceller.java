package g_mungus.zps.util;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

public class ZPSMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return !isCreatePresent() && mixinClassName.equals("g_mungus.zps.mixin.create.GoggleInfoExtension");
    }

    // mod list is not available early enough
    boolean isCreatePresent() {
        boolean createPresent;
        try {
            Class.forName(
                    "com.simibubi.create.Create",
                    false,
                    ZPSMixinCanceller.class.getClassLoader()
            );
            createPresent = true;
        } catch (ClassNotFoundException e) {
            createPresent = false;
        }
        return createPresent;
    }
}
