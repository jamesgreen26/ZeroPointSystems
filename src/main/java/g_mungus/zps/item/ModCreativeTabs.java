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

import static g_mungus.zps.item.ModItems.*;

@SuppressWarnings("unused")
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZPSMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ZPS_TAB = CREATIVE_MODE_TABS.register("zps_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab"))
            .icon(() -> new ItemStack(DENSE_CABLE_SEPARATOR.get()))
            .displayItems((parameters, output) -> addAll(output,
                    OCTO_CONTROLLER,
                    DENSE_CABLE_SEPARATOR,
                    CABLE_INSULATION,
                    SWITCH_PANEL,
                    GRADUATED_LEVER,
                    STEPUP_TRANSFORMER,
                    STEPDOWN_TRANSFORMER,
                    REDSTONE_CONVERTER,
                    SERIAL_BUS,
                    DENSE_CABLES,
                    CABLE,

                    LIGHT_PIPE,
                    SCRIPT_TERMINAL,
                    SCRIPT_TRANSMITTER,
                    DATA_TRANSCRIBER,
                    SCRIPT_COMPARATOR,
                    TEXT_DISPLAY,
                    RADIO_TRANSMITTER,
                    RADIO_RECEIVER,
                    RADIO_ANTENNA,

                    BAUXITE,
                    LITHIUM_ORE,
                    DEEPSLATE_LITHIUM_ORE,
                    ALUMINUM_INGOT,
                    ALUMINUM_NUGGET,
                    ALUMINUM_BLOCK,
                    RAW_LITHIUM,
                    LITHIUM_INGOT,
                    LITHIUM_NUGGET,
                    LITHIUM_BLOCK,

                    SPACE_METAL_INGOT,
                    SPACE_METAL_PLATE,
                    SPACE_METAL_MESH,
                    SPACE_METAL_ROD,
                    SPACE_METAL_PIPE,
                    SPACE_METAL_SCREW,
                    SPACE_METAL_BOLT,
                    SPACE_METAL_SPRING,
                    CAPACITOR,
                    TRANSISTOR,
                    MODULATOR,
                    COPPER_MAGNETRON,
                    GOLD_MAGNETRON,
                    COPPER_WIRE,
                    GOLD_WIRE,
                    VERDITE_WIRE,
                    EMPTY_SPOOL,
                    COPPER_SPOOL,
                    GOLD_SPOOL,
                    VERDITE_SPOOL
            )).build());

    public static final RegistryObject<CreativeModeTab> ZPS_DECO_TAB = CREATIVE_MODE_TABS.register("zps_tab_deco",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab_deco"))
            .icon(() -> new ItemStack(SPACE_TRUSS.get()))
            .displayItems((parameters, output) -> {
                addAll(output,
                        SPACE_TRUSS,
                        SPACE_SCAFFOLD,
                        SPACE_GRATING_BLOCK,
                        SPACE_MESH_BLOCK,
                        CATWALK,
                        CATWALK_STAIRS,
                        CAUTION_BLOCK,
                        RADIATION_CAUTION_BLOCK,
                        VOID_CAUTION_BLOCK
                );
                DYNAMIC_ITEMS.forEach(item -> output.accept(item.get()));
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