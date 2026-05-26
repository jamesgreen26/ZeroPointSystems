package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Comparator;
import java.util.function.Predicate;

import static g_mungus.zps.item.ModItems.*;

@SuppressWarnings("unused")
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZPSMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ZPS_TAB = CREATIVE_MODE_TABS.register("zps_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.zps_tab"))
            .icon(() -> new ItemStack(DENSE_CABLE_SEPARATOR.get()))
            .displayItems((parameters, output) -> {
                addAll(output,
                        OCTO_CONTROLLER,
                        DODECA_CONTROLLER,
                        DENSE_CABLE_SEPARATOR,
                        SWITCH_PANEL,
                        GRADUATED_LEVER,
                        STEPUP_TRANSFORMER,
                        STEPDOWN_TRANSFORMER,
                        REDSTONE_CONVERTER,
                        SERIAL_BUS,
                        DENSE_CABLES,
                        CABLE,
                        LIGHT_PIPE,
                        CABLE_INSULATION,

                        SCRIPT_TERMINAL,
                        DATA_LECTERN,
                        DATA_TRANSCRIBER,
                        DATA_COMPARATOR,
                        DATA_COMBINATOR,
                        TEXT_DISPLAY,
                        LOUDSPEAKER,
                        RADIO_TRANSMITTER,
                        RADIO_RECEIVER,
                        RADIO_ANTENNA,

                        ADDRESS_PAD,

                        BAUXITE,
                        LITHIUM_ORE,
                        DEEPSLATE_LITHIUM_ORE,

                        RAW_LITHIUM_BLOCK,
                        ALUMINUM_BLOCK,
                        LITHIUM_BLOCK,

                        RAW_LITHIUM,
                        ALUMINUM_INGOT,
                        LITHIUM_INGOT,
                        SPACE_METAL_INGOT,
                        ALUMINUM_NUGGET,
                        LITHIUM_NUGGET,


//                        SPACE_METAL_PLATE,
                        SPACE_METAL_MESH,
                        SPACE_METAL_ROD
//                        SPACE_METAL_PIPE,
//                        SPACE_METAL_SCREW,
//                        SPACE_METAL_BOLT,
//                        SPACE_METAL_SPRING,
//                        CAPACITOR,
//                        TRANSISTOR,
//                        MODULATOR,
//                        COPPER_MAGNETRON,
//                        GOLD_MAGNETRON,
//                        COPPER_WIRE,
//                        GOLD_WIRE,
//                        VERDITE_WIRE,
//                        EMPTY_SPOOL,
//                        COPPER_SPOOL,
//                        GOLD_SPOOL,
//                        VERDITE_SPOOL
                );
                addPaintings(parameters, output);
            }).build());

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
                        SMALL_INDUSTRIAL_LIGHT,
                        INDUSTRIAL_LIGHT,
                        LARGE_INDUSTRIAL_LIGHT,
                        CAUTION_BLOCK,
                        RADIATION_CAUTION_BLOCK,
                        VOID_CAUTION_BLOCK
                );
                DYNAMIC_ITEMS.forEach(item -> output.accept(item.get()));
            }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
        eventBus.addListener(ModCreativeTabs::addToVanillaTabs);
    }

    public static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(ModItems.REDSTONE_WAND.get());
        }
    }

    private static void addAll(CreativeModeTab.Output output, RegistryObject<?>... items) {
        for (var entry : items) {
            var it = entry.get();
            if (it instanceof Item item) {
                output.accept(item);
            }
        }
    }

    private static void addPaintings(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        parameters.holders().lookup(Registries.PAINTING_VARIANT).ifPresent((arg2x) -> generatePresetPaintings(output, arg2x, (arg) -> arg.is(PaintingVariantTags.PLACEABLE)));
    }

    private static void generatePresetPaintings(CreativeModeTab.Output arg, HolderLookup.RegistryLookup<PaintingVariant> arg2, Predicate<Holder<PaintingVariant>> predicate) {
        arg2.listElements().filter(predicate).sorted(PAINTING_COMPARATOR).forEach((arg3x) -> {
            if (arg3x.key().location().getNamespace().equals(ZPSMod.MOD_ID)) {
                ItemStack itemstack = new ItemStack(Items.PAINTING);
                CompoundTag compoundtag = itemstack.getOrCreateTagElement("EntityTag");
                Painting.storeVariant(compoundtag, arg3x);
                arg.accept(itemstack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        });
    }

    private static final Comparator<Holder<PaintingVariant>> PAINTING_COMPARATOR = Comparator.comparing(Holder::value, Comparator.comparingInt((PaintingVariant arg) -> arg.getHeight() * arg.getWidth()).thenComparing(PaintingVariant::getWidth));
} 
