package g_mungus.zps.util;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

public class ZPSMixinCanceller implements MixinCanceller {
    private static final boolean CREATE_PRESENT =
            LoadingModList.get().getModFileById("create") != null;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return !CREATE_PRESENT && mixinClassName.equals("g_mungus.zps.mixin.create.GoggleInfoExtension");
    }
}
