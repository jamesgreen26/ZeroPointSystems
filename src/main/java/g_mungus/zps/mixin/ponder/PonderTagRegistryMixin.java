package g_mungus.zps.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.zps.client.ponder.ZPSPonderTags;
import g_mungus.zps.networking.ExecutorBlocksS2CPacket;
import g_mungus.zps.networking.GetterBlocksS2CPacket;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = PonderTagRegistry.class, remap = false)
public abstract class PonderTagRegistryMixin {

    @Shadow
    public abstract PonderTag getRegisteredTag(ResourceLocation tagLocation);

    @WrapMethod(method = "getItems(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Set;")
    public Set<ResourceLocation> getItemsWrap(ResourceLocation tag, Operation<Set<ResourceLocation>> original) {
        Set<ResourceLocation> out = new HashSet<>(original.call(tag));

        if (tag.equals(ZPSPonderTags.HAS_SCRIPT_CAPS)) {
            out.addAll(ExecutorBlocksS2CPacket.command_capable_blocks);
            out.addAll(GetterBlocksS2CPacket.getter_capable_blocks);
            out.removeIf(candidate -> !zps$isDynamicScriptCapItem(candidate));
        }

        return out;
    }

    @WrapMethod(method = "getTags(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Set;")
    public Set<PonderTag> getTagsWrap(ResourceLocation item, Operation<Set<PonderTag>> original) {
        Set<PonderTag> out = new HashSet<>(original.call(item));

        if (zps$hasDynamicScriptCaps(item)) {
            out.add(getRegisteredTag(ZPSPonderTags.HAS_SCRIPT_CAPS));
        }

        return out;
    }

    @Unique
    private static boolean zps$isDynamicScriptCapItem(ResourceLocation item) {
        return zps$hasDynamicScriptCaps(item)
                && zps$isInAnyParentCreativeTab(item);
    }

    @Unique
    private static boolean zps$hasDynamicScriptCaps(ResourceLocation item) {
        return ExecutorBlocksS2CPacket.command_capable_blocks.contains(item)
                || GetterBlocksS2CPacket.getter_capable_blocks.contains(item);
    }

    @Unique
    private static boolean zps$isInAnyParentCreativeTab(ResourceLocation blockId) {
        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            return false;
        }

        Item item = BuiltInRegistries.BLOCK.get(blockId).asItem();
        return CreativeModeTabs.allTabs()
                .stream()
                .filter(tab -> tab.getType() == CreativeModeTab.Type.CATEGORY)
                .flatMap(tab -> tab.getDisplayItems().stream())
                .map(ItemStack::getItem)
                .anyMatch(item::equals);
    }
}
