package g_mungus.zps;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.CoalBurnerBlockEntity;
import g_mungus.zps.blockentity.CreativePowerCellBlockEntity;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.RollingMillBlockEntity;
import g_mungus.zps.client.ClientSetup;
import g_mungus.zps.client.ponder.ZPSPonderPlugin;
import g_mungus.zps.client.renderer.ZPSPartialModels;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.gametest.EnumPropertyWithAliasesGameTests;
import g_mungus.zps.gametest.RoboticArmGameTests;
import g_mungus.zps.gametest.RollingMillGameTests;
import g_mungus.zps.item.ModCreativeTabs;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.item.PoweredToolItem;
import g_mungus.zps.gametest.CableNetworkGameTests;
import g_mungus.zps.gametest.TextDisplayGameTests;
import g_mungus.zps.menu.ModMenus;
import g_mungus.zps.networking.ZPSGamePackets;
import g_mungus.zps.recipe.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ZPSMod.MOD_ID)
public final class ZPSMod {
    public static final String MOD_ID = "zps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ZPSMod(IEventBus modEventBus, Dist dist, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ZPSConfig.CONFIG_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ZPSConfig.SERVER_CONFIG_SPEC);
        DataFixerUpper.registerAliases();

        // Register blocks and block entities
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(ZPSGamePackets::register);
        modEventBus.addListener(ZPSMod::registerGameTests);
        modEventBus.addListener(ZPSMod::registerCapabilities);

        if (dist == Dist.CLIENT) {
            ZPSPonderPlugin.registerPlugin();
            ZPSPartialModels.init();
            modEventBus.register(ClientSetup.class);
        }

        Compat.onModInit(modEventBus);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(TextDisplayGameTests.class);
        event.register(CableNetworkGameTests.class);
        event.register(EnumPropertyWithAliasesGameTests.class);
        event.register(RoboticArmGameTests.class);
        event.register(RollingMillGameTests.class);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CELL.get(), PowerCellBlockEntity::getEnergyStorage);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.POWER_CELL.get(), PowerCellBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CREATIVE_POWER_CELL.get(), CreativePowerCellBlockEntity::getEnergyStorage);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CREATIVE_POWER_CELL.get(), CreativePowerCellBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.COAL_BURNER.get(), CoalBurnerBlockEntity::getEnergyStorage);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.COAL_BURNER.get(), CoalBurnerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ROBOTIC_ARM.get(), RoboticArmBlockEntity::getEnergyStorage);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ROLLING_MILL.get(), RollingMillBlockEntity::getEnergyStorage);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ROLLING_MILL.get(), RollingMillBlockEntity::getItemHandler);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ignored) -> new PoweredToolItem.StackEnergyStorage(stack),
                ModItems.POWER_DRILL.get(), ModItems.CHAINSAW.get());
    }
}
