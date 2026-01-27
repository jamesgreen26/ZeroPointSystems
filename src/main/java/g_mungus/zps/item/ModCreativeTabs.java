package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZPSMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ZPS_TAB = CREATIVE_MODE_TABS.register("zps_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab"))
            .icon(() -> new ItemStack(ModItems.DENSE_CABLE_SEPARATOR.get()))
            .displayItems((parameters, output) -> {
                addAll(output,
                        ModItems.OCTO_CONTROLLER,
                        ModItems.DENSE_CABLE_SEPARATOR,
                        ModItems.CABLE_INSULATION,
                        ModItems.STEPUP_TRANSFORMER,
                        ModItems.STEPDOWN_TRANSFORMER,
                        ModItems.REDSTONE_CONVERTER,
                        ModItems.DENSE_CABLES,
                        ModItems.CABLE,
                        ModItems.LIGHT_PIPE,
                        ModItems.SCRIPT_TRANSMITTER,
                        ModItems.TEXT_DISPLAY,
                        ModItems.RADIO_TRANSMITTER,
                        ModItems.RADIO_RECEIVER,

                        ModItems.SPACE_METAL_INGOT,
                        ModItems.SPACE_METAL_PLATE,
                        ModItems.SPACE_METAL_MESH,
                        ModItems.SPACE_METAL_ROD,
                        ModItems.SPACE_METAL_PIPE,
                        ModItems.SPACE_METAL_SCREW,
                        ModItems.SPACE_METAL_BOLT,
                        ModItems.SPACE_METAL_SPRING,
                        ModItems.CAPACITOR,
                        ModItems.TRANSISTOR,
                        ModItems.MODULATOR,
                        ModItems.COPPER_MAGNETRON,
                        ModItems.GOLD_MAGNETRON,
                        ModItems.COPPER_WIRE,
                        ModItems.GOLD_WIRE,
                        ModItems.VERDITE_WIRE,
                        ModItems.EMPTY_SPOOL,
                        ModItems.COPPER_SPOOL,
                        ModItems.GOLD_SPOOL,
                        ModItems.VERDITE_SPOOL
                );
            }).build());

    public static final RegistryObject<CreativeModeTab> ZPS_DECO_TAB = CREATIVE_MODE_TABS.register("zps_tab_deco",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab_deco"))
            .icon(() -> new ItemStack(ModItems.SPACE_TRUSS.get()))
            .displayItems((parameters, output) -> {
                addAll(output,
                        ModItems.SPACE_TRUSS,
                        ModItems.SPACE_SCAFFOLD,
                        ModItems.SPACE_GRATING_BLOCK,
                        ModItems.SPACE_MESH_BLOCK,
                        ModItems.CATWALK,
                        ModItems.CATWALK_STAIRS,
                        ModItems.CAUTION_BLOCK,
                        ModItems.RADIATION_CAUTION_BLOCK,
                        ModItems.VOID_CAUTION_BLOCK
                );
                ModItems.DYNAMIC_ITEMS.forEach(item -> output.accept(item.get()));
            }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }

    private static void addAll(CreativeModeTab.Output output, RegistryObject<?>... items) {
        for (var entry : items) {
            var it = entry.get();
            if (it instanceof Item item) {
                output.accept(item);
            }
        }
    }
} 