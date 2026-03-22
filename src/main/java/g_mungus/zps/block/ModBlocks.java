package g_mungus.zps.block;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.*;
import g_mungus.zps.block.cableNetwork.light_pipe.*;
import g_mungus.zps.block.datagen.BlockDataGenerator;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.mixin.BlockBehaviourAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("unused")
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = 
        DeferredRegister.create(ForgeRegistries.BLOCKS, ZPSMod.MOD_ID);

    static RegistryObject<Block> blockLookup(String name) {
        for (var block: BLOCKS.getEntries()) {
            ResourceLocation id = block.getId();
            if (id != null && id.getPath().equals(name)) {
                return block;
            }
        }
        throw new RuntimeException("RegistryObject not present for block: " + name);
    }

    public static final RegistryObject<Block> CABLE = BLOCKS.register("cable",
        () -> new CableBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
            .strength(2.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final RegistryObject<Block> DENSE_CABLES = BLOCKS.register("dense_cables",
        () -> new DenseCablesBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
            .strength(2.5f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final RegistryObject<Block> DENSE_CABLE_SEPARATOR = BLOCKS.register("dense_cable_separator",
            () -> new DenseCableSeparatorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> STEPUP_TRANSFORMER = BLOCKS.register("stepup_transformer",
            () -> new StepupTransformerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> STEPDOWN_TRANSFORMER = BLOCKS.register("stepdown_transformer",
            () -> new StepdownTransformerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> REDSTONE_CONVERTER = BLOCKS.register("redstone_converter",
            () -> new RedstoneConverterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> OCTO_CONTROLLER = BLOCKS.register("octo_controller",
            () -> new OctoControllerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> DODECA_CONTROLLER = BLOCKS.register("dodeca_controller",
            () -> new DodecaControllerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> SWITCH_PANEL = BLOCKS.register("switch_panel",
            () -> new SwitchPanelBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> GRADUATED_LEVER = BLOCKS.register("graduated_lever",
            () -> new GraduatedLeverBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> CABLE_INSULATION = BLOCKS.register("cable_insulation",
            () -> new CableInsulationBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(1.5f)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> LIGHT_PIPE = BLOCKS.register("light_pipe_cable",
            () -> new LightPipeCableBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> DATA_LECTERN = BLOCKS.register("data_lectern",
            () -> new DataLecternBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> TEXT_DISPLAY = BLOCKS.register("text_display",
            () -> new TextDisplayBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> DATA_TRANSCRIBER = BLOCKS.register("data_transcriber",
            () -> new DataTranscriberBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> RADIO_TRANSMITTER = BLOCKS.register("radio_transmitter",
            () -> new RadioTransmitter(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> RADIO_RECEIVER = BLOCKS.register("radio_receiver",
            () -> new RadioReceiver(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> RADIO_ANTENNA = BLOCKS.register("radio_antenna",
            () -> new RadioAntenna(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> DATA_COMPARATOR = BLOCKS.register("data_comparator",
            () -> new DataComparator(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> SERIAL_BUS = BLOCKS.register("serial_bus",
            () -> new SerialBusBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> SCRIPT_TERMINAL = BLOCKS.register("script_terminal",
            () -> new ScriptTerminalBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> LOUDSPEAKER = BLOCKS.register("loudspeaker",
            () -> new LoudspeakerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));


    /// RESOURCE BLOCKS
    public static final RegistryObject<Block> BAUXITE = BLOCKS.register("bauxite",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.ANDESITE)
                    .sound(SoundType.NETHERRACK)
            )
    );

    public static final RegistryObject<DropExperienceBlock> LITHIUM_ORE = BLOCKS.register("lithium_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DIAMOND_ORE)

            )
    );

    public static final RegistryObject<DropExperienceBlock> DEEPSLATE_LITHIUM_ORE = BLOCKS.register("deepslate_lithium_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DIAMOND_ORE)

            )
    );
    public static final RegistryObject<Block> ALUMINUM_BLOCK = BLOCKS.register("aluminum_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK)
            )
    );
    public static final RegistryObject<Block> LITHIUM_BLOCK = BLOCKS.register("lithium_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK)
            )
    );
    public static final RegistryObject<Block> RAW_LITHIUM_BLOCK = BLOCKS.register("raw_lithium_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.RAW_IRON_BLOCK)
            )
    );
    public static final RegistryObject<Block> ALUMINUM_PLATING = BLOCKS.register("aluminum_plating",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK)
            )
    );


/// DECOR BLOCKS

    public static final RegistryObject<Block> SPACE_TRUSS = BLOCKS.register("space_truss",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> SPACE_SCAFFOLD = BLOCKS.register("space_scaffold",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> SPACE_MESH_BLOCK = BLOCKS.register("space_mesh_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
            )
    );

    public static final RegistryObject<Block> SPACE_GRATING_BLOCK = BLOCKS.register("space_grating_block",
            () -> new GlassBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> CATWALK = BLOCKS.register("catwalk",
            () -> new CatwalkBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> CATWALK_STAIRS = BLOCKS.register("catwalk_stairs",
            () -> new CatwalkStairsBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> SMALL_INDUSTRIAL_LIGHT = BLOCKS.register("small_industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.copy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{5.5, 0.0, 5.5, 10.5, 2.0, 10.5}})
    );

    public static final RegistryObject<Block> INDUSTRIAL_LIGHT = BLOCKS.register("industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.copy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{4.0, 0.0, 4.0, 12.0, 4.0, 12.0}})
    );

    public static final RegistryObject<Block> LARGE_INDUSTRIAL_LIGHT = BLOCKS.register("large_industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.copy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{3.0, 0.0, 3.0, 13.0, 6.0, 13.0}})
    );


/// CAUTION BLOCKS

    public static final RegistryObject<Block> CAUTION_BLOCK = BLOCKS.register("caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final RegistryObject<Block> RADIATION_CAUTION_BLOCK = BLOCKS.register("radiation_caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final RegistryObject<Block> VOID_CAUTION_BLOCK = BLOCKS.register("void_caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );


/// SPACE PLATING VARIANTS

    public static final RegistryObject<Block> SPACE_PLATING = BLOCKS.register("space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> RIVETED_SPACE_PLATING = BLOCKS.register("riveted_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f)
                    .explosionResistance(6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> RED_SPACE_PLATING = BLOCKS.register("red_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> ORANGE_SPACE_PLATING = BLOCKS.register("orange_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> YELLOW_SPACE_PLATING = BLOCKS.register("yellow_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> GREEN_SPACE_PLATING = BLOCKS.register("green_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> LIME_SPACE_PLATING = BLOCKS.register("lime_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> CYAN_SPACE_PLATING = BLOCKS.register("cyan_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> BLUE_SPACE_PLATING = BLOCKS.register("blue_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> LIGHT_BLUE_SPACE_PLATING = BLOCKS.register("light_blue_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> PURPLE_SPACE_PLATING = BLOCKS.register("purple_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> MAGENTA_SPACE_PLATING = BLOCKS.register("magenta_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> PINK_SPACE_PLATING = BLOCKS.register("pink_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> BROWN_SPACE_PLATING = BLOCKS.register("brown_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> BLACK_SPACE_PLATING = BLOCKS.register("black_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> GRAY_SPACE_PLATING = BLOCKS.register("gray_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> LIGHT_GRAY_SPACE_PLATING = BLOCKS.register("light_gray_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> CHROME_SPACE_PLATING = BLOCKS.register("chrome_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final RegistryObject<Block> AGATE_SPACE_PLATING = BLOCKS.register("agate_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );


    static {
        for (var entry : BlockDataGenerator.blocksToDatagen.entrySet()) {
            for (var type : entry.getValue()) {
                switch (type) {
                    case simple -> {
                        /* block registered manually */

                        String id = entry.getKey();
                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(blockLookup(entry.getKey()).get(), new Item.Properties())));
                    }
                    case slab -> {
                        String id = entry.getKey() + "_slab";
                        RegistryObject<Block> slab = BLOCKS.register(id,
                                () -> new SlabBlock(((BlockBehaviourAccessor) blockLookup(entry.getKey()).get()).getProperties()));

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(slab.get(), new Item.Properties())));
                    }
                    case stairs -> {
                        String id = entry.getKey() + "_stairs";
                        RegistryObject<Block> stairs = BLOCKS.register(id,
                                () -> {
                                    Block block = blockLookup(entry.getKey()).get();
                                    return new StairBlock(block::defaultBlockState, ((BlockBehaviourAccessor) block).getProperties());
                                });

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(stairs.get(), new Item.Properties())));
                    }

                    case wall -> {
                        String id = entry.getKey() + "_wall";
                        RegistryObject<Block> wall = BLOCKS.register(id,
                                () -> {
                                    Block block = blockLookup(entry.getKey()).get();
                                    return new WallBlock(BlockBehaviour.Properties.copy(block).forceSolidOn());
                                });

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(wall.get(), new Item.Properties())));
                    }
                }
            }
        }
    }
} 
