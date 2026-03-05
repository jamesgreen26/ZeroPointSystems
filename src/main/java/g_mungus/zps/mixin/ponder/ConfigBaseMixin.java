package g_mungus.zps.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.createmod.catnip.config.ConfigBase;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(value = ConfigBase.CValue.class, remap = false)
public class ConfigBaseMixin<V, T extends ForgeConfigSpec.ConfigValue<V>> {

    @Shadow @Nullable protected ForgeConfigSpec.ConfigValue<V> value;

    // Allow accessing the config earlier during world loading
    @WrapMethod(method = "get", remap = false)
    public V getWrap(Operation<V> original) {
        try {
            return original.call();
        } catch (Exception e) {
            if (value == null) throw e;
            return value.getDefault();
        }
    }
}
