package g_mungus.item;

import g_mungus.ZPSMod;
import g_mungus.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, ZPSMod.MOD_ID);

    public static final RegistryObject<Item> OCTO_CONTROLLER = ITEMS.register("octo_controller",
            () -> new BlockItem(ModBlocks.OCTO_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> DENSE_CABLES = ITEMS.register("dense_cables",
        () -> new BlockItem(ModBlocks.DENSE_CABLES.get(), new Item.Properties()));

    public static final RegistryObject<Item> DENSE_CABLE_BEND = ITEMS.register("dense_cable_bend",
        () -> new BlockItem(ModBlocks.DENSE_CABLE_BEND.get(), new Item.Properties()));

    public static final RegistryObject<Item> DENSE_CABLE_SEPARATOR = ITEMS.register("dense_cable_separator",
            () -> new BlockItem(ModBlocks.DENSE_CABLE_SEPARATOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> STEPUP_TRANSFORMER = ITEMS.register("stepup_transformer",
            () -> new BlockItem(ModBlocks.STEPUP_TRANSFORMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> STEPDOWN_TRANSFORMER = ITEMS.register("stepdown_transformer",
            () -> new BlockItem(ModBlocks.STEPDOWN_TRANSFORMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> REDSTONE_CONVERTER = ITEMS.register("redstone_converter",
            () -> new BlockItem(ModBlocks.REDSTONE_CONVERTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CABLE = ITEMS.register("cable",
            () -> new BlockItem(ModBlocks.CABLE.get(), new Item.Properties()));
} 