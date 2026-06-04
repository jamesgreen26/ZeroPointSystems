package g_mungus.zps.util;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class ZPSMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return !isCreatePresent() && mixinClassName.equals("g_mungus.zps.mixin.create.GoggleInfoExtension");
    }

    // mod list is not available early enough
    boolean isCreatePresent() {
        try {
            return ZPSMixinCanceller.class.getClassLoader().getResource("com/simibubi/create/Create.class") != null;
        } catch (Exception e) {
            return false;
        }
    }
}
