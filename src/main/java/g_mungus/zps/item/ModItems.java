package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(Registries.ITEM, ZPSMod.MOD_ID);

    public static final DeferredHolder<Item, Item> OCTO_CONTROLLER = ITEMS.register("octo_controller",
            () -> new BlockItem(ModBlocks.OCTO_CONTROLLER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DENSE_CABLES = ITEMS.register("dense_cables",
        () -> new BlockItem(ModBlocks.DENSE_CABLES.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DENSE_CABLE_BEND = ITEMS.register("dense_cable_bend",
        () -> new BlockItem(ModBlocks.DENSE_CABLE_BEND.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DENSE_CABLE_SEPARATOR = ITEMS.register("dense_cable_separator",
            () -> new BlockItem(ModBlocks.DENSE_CABLE_SEPARATOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> STEPUP_TRANSFORMER = ITEMS.register("stepup_transformer",
            () -> new BlockItem(ModBlocks.STEPUP_TRANSFORMER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> STEPDOWN_TRANSFORMER = ITEMS.register("stepdown_transformer",
            () -> new BlockItem(ModBlocks.STEPDOWN_TRANSFORMER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> REDSTONE_CONVERTER = ITEMS.register("redstone_converter",
            () -> new BlockItem(ModBlocks.REDSTONE_CONVERTER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CABLE = ITEMS.register("cable",
            () -> new BlockItem(ModBlocks.CABLE.get(), new Item.Properties()));
} 