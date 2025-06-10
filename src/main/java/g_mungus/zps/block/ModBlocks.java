package g_mungus.zps.block;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = 
        DeferredRegister.create(Registries.BLOCK, ZPSMod.MOD_ID);

    public static final DeferredHolder<Block, Block> CABLE = BLOCKS.register("cable",
        () -> new CableBlock(BlockBehaviour.Properties.of()
            .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
            .strength(2.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredHolder<Block, Block> DENSE_CABLES = BLOCKS.register("dense_cables",
        () -> new DenseCablesBlock(BlockBehaviour.Properties.of()
            .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
            .strength(2.5f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredHolder<Block, Block> DENSE_CABLE_BEND = BLOCKS.register("dense_cable_bend",
        () -> new DenseCableBend(BlockBehaviour.Properties.of()
            .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
            .strength(2.5f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredHolder<Block, Block> DENSE_CABLE_SEPARATOR = BLOCKS.register("dense_cable_separator",
            () -> new DenseCableSeparatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredHolder<Block, Block> STEPUP_TRANSFORMER = BLOCKS.register("stepup_transformer",
            () -> new StepupTransformerBlock(BlockBehaviour.Properties.of()
                    .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredHolder<Block, Block> STEPDOWN_TRANSFORMER = BLOCKS.register("stepdown_transformer",
            () -> new StepdownTransformerBlock(BlockBehaviour.Properties.of()
                    .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredHolder<Block, Block> REDSTONE_CONVERTER = BLOCKS.register("redstone_converter",
            () -> new RedstoneConverterBlock(BlockBehaviour.Properties.of()
                    .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredHolder<Block, Block> OCTO_CONTROLLER = BLOCKS.register("octo_controller",
            () -> new OctoControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(Blocks.IRON_BLOCK.defaultMapColor())
                    .strength(2.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
} 