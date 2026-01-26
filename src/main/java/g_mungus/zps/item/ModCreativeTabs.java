package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZPSMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ZPS_TAB = CREATIVE_MODE_TABS.register("zps_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab"))
            .icon(() -> new ItemStack(ModItems.DENSE_CABLE_SEPARATOR.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.OCTO_CONTROLLER.get());
                output.accept(ModItems.DENSE_CABLE_SEPARATOR.get());
                output.accept(ModItems.CABLE_INSULATION.get());
                output.accept(ModItems.STEPUP_TRANSFORMER.get());
                output.accept(ModItems.STEPDOWN_TRANSFORMER.get());
                output.accept(ModItems.REDSTONE_CONVERTER.get());
                output.accept(ModItems.DENSE_CABLES.get());
                output.accept(ModItems.CABLE.get());
                output.accept(ModItems.LIGHT_PIPE.get());
                output.accept(ModItems.SCRIPT_TRANSMITTER.get());
                output.accept(ModItems.TEXT_DISPLAY.get());
            }).build());

    public static final RegistryObject<CreativeModeTab> ZPS_DECO_TAB = CREATIVE_MODE_TABS.register("zps_tab_deco",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.zps_tab_deco"))
                    .icon(() -> new ItemStack(ModItems.SPACE_TRUSS.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SPACE_TRUSS.get());
                        output.accept(ModItems.SPACE_SCAFFOLD.get());
                        output.accept(ModItems.SPACE_GRATING_BLOCK.get());
                        output.accept(ModItems.SPACE_MESH_BLOCK.get());
                        output.accept(ModItems.CATWALK.get());
                        output.accept(ModItems.CATWALK_STAIRS.get());
                        output.accept(ModItems.CAUTION_BLOCK.get());
                        output.accept(ModItems.RADIATION_CAUTION_BLOCK.get());
                        output.accept(ModItems.VOID_CAUTION_BLOCK.get());
                        ModItems.DYNAMIC_ITEMS.forEach(item -> output.accept(item.get()));
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
} 