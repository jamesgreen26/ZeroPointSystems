package g_mungus.zps.block;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.*;
import g_mungus.zps.block.cableNetwork.light_pipe.*;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import g_mungus.zps.block.datagen.BlockDataGenerator;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.mixin.BlockBehaviourAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZPSMod.MOD_ID);

    static DeferredBlock<? extends Block> blockLookup(String name) {
        for (var block: BLOCKS.getEntries()) {
            ResourceLocation id = block.getId();
            if (id != null && id.getPath().equals(name)) {
                return (DeferredBlock<? extends Block>) block;
            }
        }
        throw new RuntimeException("RegistryObject not present for block: " + name);
    }

    private static BlockBehaviour.Properties cableProperties(float strength) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(strength)
                .requiresCorrectToolForDrops()
                .isRedstoneConductor(ModBlocks::isCableRedstoneConductor);
    }

    private static boolean isCableRedstoneConductor(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.hasProperty(CableBlock.INSULATION_TYPE)
                || state.getValue(CableBlock.INSULATION_TYPE) != InsulationType.GRATING;
    }

    public static final DeferredBlock<Block> CABLE = BLOCKS.register("cable",
        () -> new CableBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> DENSE_CABLES = BLOCKS.register("dense_cables",
        () -> new DenseCablesBlock(cableProperties(2.5f)));

    public static final DeferredBlock<Block> DENSE_CABLE_SEPARATOR = BLOCKS.register("dense_cable_separator",
            () -> new DenseCableSeparatorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            ));

    public static final DeferredBlock<Block> STEPUP_TRANSFORMER = BLOCKS.register("stepup_transformer",
            () -> new StepupTransformerBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> STEPDOWN_TRANSFORMER = BLOCKS.register("stepdown_transformer",
            () -> new StepdownTransformerBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> REDSTONE_CONVERTER = BLOCKS.register("redstone_converter",
            () -> new RedstoneConverterBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> OCTO_CONTROLLER = BLOCKS.register("octo_controller",
            () -> new OctoControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> DODECA_CONTROLLER = BLOCKS.register("dodeca_controller",
            () -> new DodecaControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> SWITCH_PANEL = BLOCKS.register("switch_panel",
            () -> new SwitchPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> GRADUATED_LEVER = BLOCKS.register("graduated_lever",
            () -> new GraduatedLeverBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> CABLE_INSULATION = BLOCKS.register("cable_insulation",
            () -> new CableInsulationBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(1.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DATA_CABLE = BLOCKS.register("data_cable",
            () -> new LightPipeCableBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> DATA_LECTERN = BLOCKS.register("data_lectern",
            () -> new DataLecternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> TEXT_DISPLAY = BLOCKS.register("text_display",
            () -> new TextDisplayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> DATA_TRANSCRIBER = BLOCKS.register("data_transcriber",
            () -> new DataTranscriberBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> RADIO_TRANSMITTER = BLOCKS.register("radio_transmitter",
            () -> new RadioTransmitter(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> RADIO_RECEIVER = BLOCKS.register("radio_receiver",
            () -> new RadioReceiver(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> RADIO_ANTENNA = BLOCKS.register("radio_antenna",
            () -> new RadioAntenna(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> DATA_COMPARATOR = BLOCKS.register("data_comparator",
            () -> new DataComparator(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> DATA_COMBINATOR = BLOCKS.register("data_combinator",
            () -> new DataCombinator(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> SERIAL_BUS = BLOCKS.register("serial_bus",
            () -> new SerialBusBlock(cableProperties(2.0f)));

    public static final DeferredBlock<Block> SCRIPT_TERMINAL = BLOCKS.register("script_terminal",
            () -> new ScriptTerminalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> LOUDSPEAKER = BLOCKS.register("loudspeaker",
            () -> new LoudspeakerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> ROBOTIC_ARM = BLOCKS.register("robotic_arm",
            () -> new RoboticArmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> INCOMPLETE_ROBOTIC_ARM_0 = BLOCKS.register("incomplete_robotic_arm_0",
            () -> new IncompleteRoboticArmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), 0));

    public static final DeferredBlock<Block> INCOMPLETE_ROBOTIC_ARM_1 = BLOCKS.register("incomplete_robotic_arm_1",
            () -> new IncompleteRoboticArmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), 1));

    public static final DeferredBlock<Block> INCOMPLETE_ROBOTIC_ARM_2 = BLOCKS.register("incomplete_robotic_arm_2",
            () -> new IncompleteRoboticArmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), 2));

    public static final DeferredBlock<Block> CREATIVE_POWER_CELL = BLOCKS.register("creative_power_cell",
            () -> new CreativePowerCellBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> POWER_CELL = BLOCKS.register("power_cell",
            () -> new PowerCellBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> COAL_BURNER = BLOCKS.register("coal_burner",
            () -> new CoalBurnerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> ROLLING_MILL = BLOCKS.register("rolling_mill",
            () -> new RollingMillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    // Don't occlude light: the BER/visual light for the wheels is sampled at the
                    // mill's own position, which would otherwise always be pitch black.
                    .noOcclusion()));

    public static final DeferredBlock<Block> ASSEMBLER = BLOCKS.register("assembler",
            () -> new AssemblerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> IMPACT_PISTON = BLOCKS.register("impact_piston",
            () -> new ImpactPistonBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    // Don't occlude light: the rod's light is sampled at the piston's own position,
                    // which would otherwise always be pitch black.
                    .noOcclusion()));


    /// RESOURCE BLOCKS
    public static final DeferredBlock<Block> BAUXITE = BLOCKS.register("bauxite",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                    .sound(SoundType.NETHERRACK)
            )
    );

    public static final DeferredBlock<DropExperienceBlock> LITHIUM_ORE = BLOCKS.register("lithium_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE)

            )
    );

    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_LITHIUM_ORE = BLOCKS.register("deepslate_lithium_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE)

            )
    );
    public static final DeferredBlock<Block> ALUMINUM_BLOCK = BLOCKS.register("aluminum_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
            )
    );
    public static final DeferredBlock<WaterExplodingBlock> LITHIUM_BLOCK = BLOCKS.register("lithium_block",
            () -> new WaterExplodingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
            )
    );
    public static final DeferredBlock<WaterExplodingBlock> RAW_LITHIUM_BLOCK = BLOCKS.register("raw_lithium_block",
            () -> new WaterExplodingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK)
            )
    );
    public static final DeferredBlock<Block> ALUMINUM_PLATING = BLOCKS.register("aluminum_plating",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
            )
    );


/// DECOR BLOCKS

    public static final DeferredBlock<Block> SPACE_TRUSS = BLOCKS.register("space_truss",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> SPACE_SCAFFOLD = BLOCKS.register("space_scaffold",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> SPACE_MESH_BLOCK = BLOCKS.register("space_mesh_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
            )
    );

    public static final DeferredBlock<SpaceGratingBlock> SPACE_GRATING_BLOCK = BLOCKS.register("space_grating_block",
            () -> new SpaceGratingBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> CATWALK = BLOCKS.register("catwalk",
            () -> new CatwalkBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> CATWALK_STAIRS = BLOCKS.register("catwalk_stairs",
            () -> new CatwalkStairsBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> SMALL_INDUSTRIAL_LIGHT = BLOCKS.register("small_industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{5.5, 0.0, 5.5, 10.5, 2.0, 10.5}})
    );

    public static final DeferredBlock<Block> INDUSTRIAL_LIGHT = BLOCKS.register("industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{4.0, 0.0, 4.0, 12.0, 4.0, 12.0}})
    );

    public static final DeferredBlock<Block> LARGE_INDUSTRIAL_LIGHT = BLOCKS.register("large_industrial_light",
            () -> new IndustrialLightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_LAMP)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                    .noOcclusion(),
                    new double[][]{{3.0, 0.0, 3.0, 13.0, 6.0, 13.0}})
    );


/// CAUTION BLOCKS

    public static final DeferredBlock<Block> CAUTION_BLOCK = BLOCKS.register("caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> RADIATION_CAUTION_BLOCK = BLOCKS.register("radiation_caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> VOID_CAUTION_BLOCK = BLOCKS.register("void_caution_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );


/// SPACE PLATING VARIANTS

    public static final DeferredBlock<Block> SPACE_PLATING = BLOCKS.register("space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> RIVETED_SPACE_PLATING = BLOCKS.register("riveted_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f)
                    .explosionResistance(6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> RED_SPACE_PLATING = BLOCKS.register("red_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> ORANGE_SPACE_PLATING = BLOCKS.register("orange_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> YELLOW_SPACE_PLATING = BLOCKS.register("yellow_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> GREEN_SPACE_PLATING = BLOCKS.register("green_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> LIME_SPACE_PLATING = BLOCKS.register("lime_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> CYAN_SPACE_PLATING = BLOCKS.register("cyan_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> BLUE_SPACE_PLATING = BLOCKS.register("blue_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> LIGHT_BLUE_SPACE_PLATING = BLOCKS.register("light_blue_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> PURPLE_SPACE_PLATING = BLOCKS.register("purple_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> MAGENTA_SPACE_PLATING = BLOCKS.register("magenta_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> PINK_SPACE_PLATING = BLOCKS.register("pink_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> BROWN_SPACE_PLATING = BLOCKS.register("brown_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> BLACK_SPACE_PLATING = BLOCKS.register("black_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> GRAY_SPACE_PLATING = BLOCKS.register("gray_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> LIGHT_GRAY_SPACE_PLATING = BLOCKS.register("light_gray_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> CHROME_SPACE_PLATING = BLOCKS.register("chrome_space_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredBlock<Block> AGATE_SPACE_PLATING = BLOCKS.register("agate_space_plating",
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
                        DeferredBlock<Block> slab = BLOCKS.register(id,
                                () -> new SlabBlock(((BlockBehaviourAccessor) blockLookup(entry.getKey()).get()).getProperties()));

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(slab.get(), new Item.Properties())));
                    }
                    case stairs -> {
                        String id = entry.getKey() + "_stairs";
                        DeferredBlock<Block> stairs = BLOCKS.register(id,
                                () -> {
                                    Block block = blockLookup(entry.getKey()).get();
                                    return new StairBlock(block.defaultBlockState(), ((BlockBehaviourAccessor) block).getProperties());
                                });

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(stairs.get(), new Item.Properties())));
                    }

                    case wall -> {
                        String id = entry.getKey() + "_wall";
                        DeferredBlock<Block> wall = BLOCKS.register(id,
                                () -> {
                                    Block block = blockLookup(entry.getKey()).get();
                                    return new WallBlock(BlockBehaviour.Properties.ofFullCopy(block).forceSolidOn());
                                });

                        ModItems.DYNAMIC_ITEMS.add(ModItems.ITEMS.register(id,
                                () -> new BlockItem(wall.get(), new Item.Properties())));
                    }
                }
            }
        }
    }
} 
