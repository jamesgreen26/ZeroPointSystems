package g_mungus.zps.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.zps.client.ponder.ZPSPonderTags;
import g_mungus.zps.networking.ExecutorBlocksS2CPacket;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = PonderTagRegistry.class, remap = false)
public class PonderTagRegistryMixin {

    @WrapMethod(method = "getItems(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Set;")
    public Set<ResourceLocation> getItemsWrap(ResourceLocation tag, Operation<Set<ResourceLocation>> original) {
        Set<ResourceLocation> out = new HashSet<>(original.call(tag));

        if (tag.equals(ZPSPonderTags.HAS_SCRIPT_CAPS)) {
            out.addAll(ExecutorBlocksS2CPacket.command_capable_blocks);
        }

        return out;
    }
}
