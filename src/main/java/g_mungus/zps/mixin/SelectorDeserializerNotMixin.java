package g_mungus.zps.mixin;

import com.google.gson.JsonObject;
import g_mungus.zps.client.model.multipart.NotCondition;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(net.minecraft.client.renderer.block.model.multipart.Selector.Deserializer.class)
public class SelectorDeserializerNotMixin {

    @WrapMethod(method = "getCondition")
    private static Condition zps$wrapGetCondition(JsonObject jsonObject, Operation<Condition> original) {
        // Handle NOT as a single-key condition type, just like OR and AND
        if (jsonObject.size() == 1) {
            if (jsonObject.has(NotCondition.TOKEN)) {
                JsonObject innerJson = GsonHelper.getAsJsonObject(jsonObject, NotCondition.TOKEN);
                Condition innerCondition = original.call(innerJson);
                return new NotCondition(innerCondition);
            }
        }
        // Delegate to original for all other cases (OR, AND, property conditions)
        return original.call(jsonObject);
    }
}
