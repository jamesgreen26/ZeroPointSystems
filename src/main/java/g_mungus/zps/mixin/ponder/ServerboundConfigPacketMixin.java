package g_mungus.zps.mixin.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.createmod.catnip.net.ServerboundConfigPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Catnip's config screen sends every changed server value to the server, but its packet only knows
 * booleans, enums and numbers: saving a list value — even just resetting the config to defaults —
 * threw and crashed the client. Lists of strings go over as a JSON array.
 */
@Mixin(value = ServerboundConfigPacket.class, remap = false)
public class ServerboundConfigPacketMixin {

    @Inject(method = "serialize", at = @At("HEAD"), cancellable = true, remap = false)
    private void zps$serializeList(Object value, CallbackInfoReturnable<String> cir) {
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(entry -> array.add(String.valueOf(entry)));
            cir.setReturnValue(array.toString());
        }
    }

    @Inject(method = "deserialize", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zps$deserializeList(Object type, String sValue, CallbackInfoReturnable<Object> cir) {
        if (type instanceof List<?>) {
            List<String> list = new ArrayList<>();
            for (JsonElement entry : JsonParser.parseString(sValue).getAsJsonArray()) {
                list.add(entry.getAsString());
            }
            cir.setReturnValue(list);
        }
    }
}
