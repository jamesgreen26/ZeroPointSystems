package g_mungus.zps.mixin;

import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockElement.Deserializer.class)
public class BlockElementMixin {

    @Shadow
    private Vector3f getVector3f(JsonObject jsonObject, String memberName) {
        throw new AssertionError();
    }

    @Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
    private void onGetAngle(JsonObject jsonObject, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GsonHelper.getAsFloat(jsonObject, "angle"));
    }

    @Inject(method = "getFrom", at = @At("HEAD"), cancellable = true)
    private void onGetFrom(JsonObject jsonObject, CallbackInfoReturnable<Vector3f> cir) {
        cir.setReturnValue(getVector3f(jsonObject, "from"));
    }

    @Inject(method = "getTo", at = @At("HEAD"), cancellable = true)
    private void onGetTo(JsonObject jsonObject, CallbackInfoReturnable<Vector3f> cir) {
        cir.setReturnValue(getVector3f(jsonObject, "to"));
    }
}
