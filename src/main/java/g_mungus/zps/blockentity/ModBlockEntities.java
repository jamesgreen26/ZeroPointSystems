package g_mungus.zps.blockentity;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZPSMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneConverterBlockEntity>> REDSTONE_CONVERTER = 
        BLOCK_ENTITIES.register("redstone_converter", 
            () -> BlockEntityType.Builder.of(RedstoneConverterBlockEntity::new, 
                ModBlocks.REDSTONE_CONVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StepUpTransformerBlockEntity>> STEPUP_TRANSFORMER = 
        BLOCK_ENTITIES.register("stepup_transformer", 
            () -> BlockEntityType.Builder.of(StepUpTransformerBlockEntity::new, 
                ModBlocks.STEPUP_TRANSFORMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StepDownTransformerBlockEntity>> STEPDOWN_TRANSFORMER = 
        BLOCK_ENTITIES.register("stepdown_transformer", 
            () -> BlockEntityType.Builder.of(StepDownTransformerBlockEntity::new, 
                ModBlocks.STEPDOWN_TRANSFORMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OctoControllerBlockEntity>> OCTO_CONTROLLER = 
        BLOCK_ENTITIES.register("octo_controller", 
            () -> BlockEntityType.Builder.of(OctoControllerBlockEntity::new, 
                ModBlocks.OCTO_CONTROLLER.get()).build(null));
} 