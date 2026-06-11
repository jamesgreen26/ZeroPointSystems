package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, ZPSMod.MOD_ID);

    public static final List<RegistryObject<Item>> DYNAMIC_ITEMS = new ArrayList<>();

    public static final RegistryObject<Item> OCTO_CONTROLLER = ITEMS.register("octo_controller",
            () -> new BlockItem(ModBlocks.OCTO_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> DODECA_CONTROLLER = ITEMS.register("dodeca_controller",
            () -> new BlockItem(ModBlocks.DODECA_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> DENSE_CABLE_SEPARATOR = ITEMS.register("dense_cable_separator",
            () -> new BlockItem(ModBlocks.DENSE_CABLE_SEPARATOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> CABLE_INSULATION = ITEMS.register("cable_insulation",
            () -> new BlockItem(ModBlocks.CABLE_INSULATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> STEPUP_TRANSFORMER = ITEMS.register("stepup_transformer",
            () -> new BlockItem(ModBlocks.STEPUP_TRANSFORMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> STEPDOWN_TRANSFORMER = ITEMS.register("stepdown_transformer",
            () -> new BlockItem(ModBlocks.STEPDOWN_TRANSFORMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> REDSTONE_CONVERTER = ITEMS.register("redstone_converter",
            () -> new BlockItem(ModBlocks.REDSTONE_CONVERTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> DENSE_CABLES = ITEMS.register("dense_cables",
            () -> new BlockItem(ModBlocks.DENSE_CABLES.get(), new Item.Properties()));

    public static final RegistryObject<Item> SWITCH_PANEL = ITEMS.register("switch_panel",
            () -> new BlockItem(ModBlocks.SWITCH_PANEL.get(), new Item.Properties()));

    public static final RegistryObject<Item> GRADUATED_LEVER = ITEMS.register("graduated_lever",
            () -> new BlockItem(ModBlocks.GRADUATED_LEVER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CABLE = ITEMS.register("cable",
            () -> new BlockItem(ModBlocks.CABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> LIGHT_PIPE = ITEMS.register("light_pipe_cable",
            () -> new BlockItem(ModBlocks.LIGHT_PIPE.get(), new Item.Properties()));

    public static final RegistryObject<Item> SERIAL_BUS = ITEMS.register("serial_bus",
            () -> new BlockItem(ModBlocks.SERIAL_BUS.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCRIPT_TERMINAL = ITEMS.register("script_terminal",
            () -> new BlockItem(ModBlocks.SCRIPT_TERMINAL.get(), new Item.Properties()));

    public static final RegistryObject<Item> DATA_LECTERN = ITEMS.register("data_lectern",
            () -> new BlockItem(ModBlocks.DATA_LECTERN.get(), new Item.Properties()));

    public static final RegistryObject<Item> DATA_TRANSCRIBER = ITEMS.register("data_transcriber",
            () -> new BlockItem(ModBlocks.DATA_TRANSCRIBER.get(), new Item.Properties()));

    public static final RegistryObject<Item> DATA_COMPARATOR = ITEMS.register("data_comparator",
            () -> new BlockItem(ModBlocks.DATA_COMPARATOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> DATA_COMBINATOR = ITEMS.register("data_combinator",
            () -> new BlockItem(ModBlocks.DATA_COMBINATOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> TEXT_DISPLAY = ITEMS.register("text_display",
            () -> new BlockItem(ModBlocks.TEXT_DISPLAY.get(), new Item.Properties()));

    public static final RegistryObject<Item> LOUDSPEAKER = ITEMS.register("loudspeaker",
            () -> new BlockItem(ModBlocks.LOUDSPEAKER.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIO_TRANSMITTER = ITEMS.register("radio_transmitter",
            () -> new BlockItem(ModBlocks.RADIO_TRANSMITTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIO_RECEIVER = ITEMS.register("radio_receiver",
            () -> new BlockItem(ModBlocks.RADIO_RECEIVER.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIO_ANTENNA = ITEMS.register("radio_antenna",
            () -> new RadioAntennaBlockItem(ModBlocks.RADIO_ANTENNA.get(), new Item.Properties()));

    public static final RegistryObject<Item> ROBOTIC_ARM = ITEMS.register("robotic_arm",
            () -> new BlockItem(ModBlocks.ROBOTIC_ARM.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREATIVE_POWER_CELL = ITEMS.register("creative_power_cell",
            () -> new BlockItem(ModBlocks.CREATIVE_POWER_CELL.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> POWER_CELL = ITEMS.register("power_cell",
            () -> new BlockItem(ModBlocks.POWER_CELL.get(), new Item.Properties()));

    public static final RegistryObject<Item> COAL_BURNER = ITEMS.register("coal_burner",
            () -> new BlockItem(ModBlocks.COAL_BURNER.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPACE_METAL_INGOT = ITEMS.register("space_metal_ingot",
        () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_PLATE = ITEMS.register("space_metal_plate",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_MESH = ITEMS.register("space_metal_mesh",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_ROD = ITEMS.register("space_metal_rod",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_PIPE = ITEMS.register("space_metal_pipe",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_SCREW = ITEMS.register("space_metal_screw",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_BOLT = ITEMS.register("space_metal_bolt",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPACE_METAL_SPRING = ITEMS.register("space_metal_spring",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BAUXITE = ITEMS.register("bauxite",
            () -> new BlockItem(ModBlocks.BAUXITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_ORE = ITEMS.register("lithium_ore",
            () -> new BlockItem(ModBlocks.LITHIUM_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEEPSLATE_LITHIUM_ORE = ITEMS.register("deepslate_lithium_ore",
            () -> new BlockItem(ModBlocks.DEEPSLATE_LITHIUM_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_INGOT = ITEMS.register("aluminum_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_NUGGET = ITEMS.register("aluminum_nugget",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_BLOCK = ITEMS.register("aluminum_block",
            () -> new BlockItem(ModBlocks.ALUMINUM_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> RAW_LITHIUM = ITEMS.register("raw_lithium",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_INGOT = ITEMS.register("lithium_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_NUGGET = ITEMS.register("lithium_nugget",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_BLOCK = ITEMS.register("lithium_block",
            () -> new BlockItem(ModBlocks.LITHIUM_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> RAW_LITHIUM_BLOCK = ITEMS.register("raw_lithium_block",
            () -> new BlockItem(ModBlocks.RAW_LITHIUM_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAPACITOR = ITEMS.register("capacitor",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TRANSISTOR = ITEMS.register("transistor",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MODULATOR = ITEMS.register("modulator",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> COPPER_MAGNETRON = ITEMS.register("copper_magnetron",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD_MAGNETRON = ITEMS.register("gold_magnetron",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> COPPER_WIRE = ITEMS.register("copper_wire",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD_WIRE = ITEMS.register("gold_wire",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VERDITE_WIRE = ITEMS.register("verdite_wire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> EMPTY_SPOOL = ITEMS.register("empty_spool",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_SPOOL = ITEMS.register("copper_spool",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD_SPOOL = ITEMS.register("gold_spool",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VERDITE_SPOOL = ITEMS.register("verdite_spool",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<RedstoneWandItem> REDSTONE_WAND = ITEMS.register("redstone_wand",
            () -> new RedstoneWandItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    public static final RegistryObject<AddressPadItem> ADDRESS_PAD = ITEMS.register("address_pad",
            () -> new AddressPadItem(new Item.Properties().stacksTo(1)));

/// DECOR ITEMS

    public static final RegistryObject<Item> SPACE_TRUSS = ITEMS.register("space_truss",
        () -> new BlockItem(ModBlocks.SPACE_TRUSS.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPACE_SCAFFOLD = ITEMS.register("space_scaffold",
            () -> new BlockItem(ModBlocks.SPACE_SCAFFOLD.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPACE_GRATING_BLOCK = ITEMS.register("space_grating_block",
            () -> new BlockItem(ModBlocks.SPACE_GRATING_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPACE_MESH_BLOCK = ITEMS.register("space_mesh_block",
            () -> new BlockItem(ModBlocks.SPACE_MESH_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> CATWALK = ITEMS.register("catwalk",
            () -> new CatwalkBlockItem(ModBlocks.CATWALK.get(), new Item.Properties()));

    public static final RegistryObject<Item> CATWALK_STAIRS = ITEMS.register("catwalk_stairs",
            () -> new CatwalkStairBlockItem(ModBlocks.CATWALK_STAIRS.get(), new Item.Properties()));

    public static final RegistryObject<Item> SMALL_INDUSTRIAL_LIGHT = ITEMS.register("small_industrial_light",
            () -> new BlockItem(ModBlocks.SMALL_INDUSTRIAL_LIGHT.get(), new Item.Properties()));

    public static final RegistryObject<Item> INDUSTRIAL_LIGHT = ITEMS.register("industrial_light",
            () -> new BlockItem(ModBlocks.INDUSTRIAL_LIGHT.get(), new Item.Properties()));

    public static final RegistryObject<Item> LARGE_INDUSTRIAL_LIGHT = ITEMS.register("large_industrial_light",
            () -> new BlockItem(ModBlocks.LARGE_INDUSTRIAL_LIGHT.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAUTION_BLOCK = ITEMS.register("caution_block",
            () -> new BlockItem(ModBlocks.CAUTION_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIATION_CAUTION_BLOCK = ITEMS.register("radiation_caution_block",
            () -> new BlockItem(ModBlocks.RADIATION_CAUTION_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> VOID_CAUTION_BLOCK = ITEMS.register("void_caution_block",
            () -> new BlockItem(ModBlocks.VOID_CAUTION_BLOCK.get(), new Item.Properties()));
} 
