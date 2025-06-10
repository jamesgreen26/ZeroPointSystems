package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZPSMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ZPS_TAB = CREATIVE_MODE_TABS.register("zps_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab"))
            .icon(() -> new ItemStack(ModItems.DENSE_CABLE_SEPARATOR.get()))
            .displayItems((parameters, output) -> {
                // Add all our items to the tab
                ModItems.ITEMS.getEntries().forEach(itemRegistryObject -> {
                    output.accept(itemRegistryObject.get());
                });
            }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
} 