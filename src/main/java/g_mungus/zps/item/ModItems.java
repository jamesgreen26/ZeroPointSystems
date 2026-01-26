package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, ZPSMod.MOD_ID);

    public static List<RegistryObject<Item>> DYNAMIC_ITEMS = new ArrayList<>();

    public static final RegistryObject<Item> OCTO_CONTROLLER = ITEMS.register("octo_controller",
            () -> new BlockItem(ModBlocks.OCTO_CONTROLLER.get(), new Item.Properties()));

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

    public static final RegistryObject<Item> CABLE = ITEMS.register("cable",
            () -> new BlockItem(ModBlocks.CABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> LIGHT_PIPE = ITEMS.register("light_pipe_cable",
            () -> new BlockItem(ModBlocks.LIGHT_PIPE.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCRIPT_TRANSMITTER = ITEMS.register("script_transmitter",
            () -> new BlockItem(ModBlocks.SCRIPT_TRANSMITTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> TEXT_DISPLAY = ITEMS.register("text_display",
            () -> new BlockItem(ModBlocks.TEXT_DISPLAY.get(), new Item.Properties()));

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
            () -> new BlockItem(ModBlocks.CATWALK.get(), new Item.Properties()));

    public static final RegistryObject<Item> CATWALK_STAIRS = ITEMS.register("catwalk_stairs",
            () -> new BlockItem(ModBlocks.CATWALK_STAIRS.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAUTION_BLOCK = ITEMS.register("caution_block",
            () -> new BlockItem(ModBlocks.CAUTION_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIATION_CAUTION_BLOCK = ITEMS.register("radiation_caution_block",
            () -> new BlockItem(ModBlocks.RADIATION_CAUTION_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> VOID_CAUTION_BLOCK = ITEMS.register("void_caution_block",
            () -> new BlockItem(ModBlocks.VOID_CAUTION_BLOCK.get(), new Item.Properties()));
} 