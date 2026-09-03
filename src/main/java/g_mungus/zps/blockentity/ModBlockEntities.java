package g_mungus.zps.blockentity;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.light_pipe.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("ConstantConditions")
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZPSMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<RedstoneConverterBlockEntity>> REDSTONE_CONVERTER = 
        BLOCK_ENTITIES.register("redstone_converter",
            () -> BlockEntityType.Builder.of(RedstoneConverterBlockEntity::new, 
                ModBlocks.REDSTONE_CONVERTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<StepUpTransformerBlockEntity>> STEPUP_TRANSFORMER = 
        BLOCK_ENTITIES.register("stepup_transformer",
            () -> BlockEntityType.Builder.of(StepUpTransformerBlockEntity::new, 
                ModBlocks.STEPUP_TRANSFORMER.get()).build(null));

    public static final RegistryObject<BlockEntityType<StepDownTransformerBlockEntity>> STEPDOWN_TRANSFORMER = 
        BLOCK_ENTITIES.register("stepdown_transformer",
            () -> BlockEntityType.Builder.of(StepDownTransformerBlockEntity::new, 
                ModBlocks.STEPDOWN_TRANSFORMER.get()).build(null));

    public static final RegistryObject<BlockEntityType<OctoControllerBlockEntity>> OCTO_CONTROLLER =
        BLOCK_ENTITIES.register("octo_controller",
            () -> BlockEntityType.Builder.of(OctoControllerBlockEntity::new,
                ModBlocks.OCTO_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<DodecaControllerBlockEntity>> DODECA_CONTROLLER =
        BLOCK_ENTITIES.register("dodeca_controller",
            () -> BlockEntityType.Builder.of(DodecaControllerBlockEntity::new,
                ModBlocks.DODECA_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SwitchPanelBlockEntity>> SWITCH_PANEL =
            BLOCK_ENTITIES.register("switch_panel",
                    () -> BlockEntityType.Builder.of(SwitchPanelBlockEntity::new,
                            ModBlocks.SWITCH_PANEL.get()).build(null));

    public static final RegistryObject<BlockEntityType<GraduatedLeverBlockEntity>> GRADUATED_LEVER =
            BLOCK_ENTITIES.register("graduated_lever",
                    () -> BlockEntityType.Builder.of(GraduatedLeverBlockEntity::new,
                            ModBlocks.GRADUATED_LEVER.get()).build(null));

    public static final RegistryObject<BlockEntityType<DataLecternBlockEntity>> DATA_LECTERN =
        BLOCK_ENTITIES.register("data_lectern",
            () -> BlockEntityType.Builder.of(DataLecternBlockEntity::new,
                ModBlocks.DATA_LECTERN.get()).build(null));

    public static final RegistryObject<BlockEntityType<TextDisplayBlockEntity>> TEXT_DISPLAY =
            BLOCK_ENTITIES.register("text_display",
                    () -> BlockEntityType.Builder.of(TextDisplayBlockEntity::new,
                            ModBlocks.TEXT_DISPLAY.get()).build(null));

    public static final RegistryObject<BlockEntityType<DataTranscriberBlockEntity>> DATA_TRANSCRIBER =
            BLOCK_ENTITIES.register("data_transcriber",
                    () -> BlockEntityType.Builder.of(DataTranscriberBlockEntity::new,
                            ModBlocks.DATA_TRANSCRIBER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER =
            BLOCK_ENTITIES.register("radio_transmitter",
                    () -> BlockEntityType.Builder.of(RadioTransmitterBlockEntity::new,
                            ModBlocks.RADIO_TRANSMITTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RadioReceiverBlockEntity>> RADIO_RECEIVER =
            BLOCK_ENTITIES.register("radio_receiver",
                    () -> BlockEntityType.Builder.of(RadioReceiverBlockEntity::new,
                            ModBlocks.RADIO_RECEIVER.get()).build(null));

    public static final RegistryObject<BlockEntityType<DataComparatorBlockEntity>> DATA_COMPARATOR =
            BLOCK_ENTITIES.register("data_comparator",
                    () -> BlockEntityType.Builder.of(DataComparatorBlockEntity::new,
                            ModBlocks.DATA_COMPARATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<DataCombinatorBlockEntity>> DATA_COMBINATOR =
            BLOCK_ENTITIES.register("data_combinator",
                    () -> BlockEntityType.Builder.of(DataCombinatorBlockEntity::new,
                            ModBlocks.DATA_COMBINATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<SerialBusBlockEntity>> SERIAL_BUS =
            BLOCK_ENTITIES.register("serial_bus",
                    () -> BlockEntityType.Builder.of(SerialBusBlockEntity::new,
                            ModBlocks.SERIAL_BUS.get()).build(null));

    public static final RegistryObject<BlockEntityType<ScriptTerminalBlockEntity>> SCRIPT_TERMINAL =
            BLOCK_ENTITIES.register("script_terminal",
                    () -> BlockEntityType.Builder.of(ScriptTerminalBlockEntity::new,
                            ModBlocks.SCRIPT_TERMINAL.get()).build(null));

    public static final RegistryObject<BlockEntityType<LoudspeakerBlockEntity>> LOUDSPEAKER =
            BLOCK_ENTITIES.register("loudspeaker",
                    () -> BlockEntityType.Builder.of(LoudspeakerBlockEntity::new,
                            ModBlocks.LOUDSPEAKER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RoboticArmBlockEntity>> ROBOTIC_ARM =
            BLOCK_ENTITIES.register("robotic_arm",
                    () -> BlockEntityType.Builder.of(RoboticArmBlockEntity::new,
                            ModBlocks.ROBOTIC_ARM.get()).build(null));

    public static final RegistryObject<BlockEntityType<CreativePowerCellBlockEntity>> CREATIVE_POWER_CELL =
            BLOCK_ENTITIES.register("creative_power_cell",
                    () -> BlockEntityType.Builder.of(CreativePowerCellBlockEntity::new,
                            ModBlocks.CREATIVE_POWER_CELL.get()).build(null));

    public static final RegistryObject<BlockEntityType<PowerCellBlockEntity>> POWER_CELL =
            BLOCK_ENTITIES.register("power_cell",
                    () -> BlockEntityType.Builder.of(PowerCellBlockEntity::new,
                            ModBlocks.POWER_CELL.get()).build(null));

    public static final RegistryObject<BlockEntityType<CoalBurnerBlockEntity>> COAL_BURNER =
            BLOCK_ENTITIES.register("coal_burner",
                    () -> BlockEntityType.Builder.of(CoalBurnerBlockEntity::new,
                            ModBlocks.COAL_BURNER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RollingMillBlockEntity>> ROLLING_MILL =
            BLOCK_ENTITIES.register("rolling_mill",
                    () -> BlockEntityType.Builder.of(RollingMillBlockEntity::new,
                            ModBlocks.ROLLING_MILL.get()).build(null));

    public static final RegistryObject<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER =
            BLOCK_ENTITIES.register("assembler",
                    () -> BlockEntityType.Builder.of(AssemblerBlockEntity::new,
                            ModBlocks.ASSEMBLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<ImpactPistonBlockEntity>> IMPACT_PISTON =
            BLOCK_ENTITIES.register("impact_piston",
                    () -> BlockEntityType.Builder.of(ImpactPistonBlockEntity::new,
                            ModBlocks.IMPACT_PISTON.get()).build(null));

    public static final RegistryObject<BlockEntityType<SieveBlockEntity>> SIEVE =
            BLOCK_ENTITIES.register("sieve",
                    () -> BlockEntityType.Builder.of(SieveBlockEntity::new,
                            ModBlocks.SIEVE.get()).build(null));

}

