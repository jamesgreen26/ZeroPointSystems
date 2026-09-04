package g_mungus.zps.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ZPSConfig {
    // Client config
    private static ModConfigSpec.ConfigValue<Boolean> terminalKeyboardSounds;
    private static final boolean terminalKeyboardSoundsDefault = true;

    /**
     * Null outside a development environment: the option is not written to the config at all, so
     * it cannot be switched on in a released build.
     */
    private static ModConfigSpec.ConfigValue<Boolean> gasPressureOverlay;

    /** Whether to tint gas nodes by pressure. Always false in production, where the option is absent. */
    public static boolean showGasPressureOverlay() {
        if (gasPressureOverlay == null) {
            return false;
        }
        try {
            return gasPressureOverlay.get();
        } catch (Exception ignored) { }
        return false;
    }

    public static boolean useKeyboardSounds() {
        boolean result = terminalKeyboardSoundsDefault;
        try {
            result = terminalKeyboardSounds.get();
        } catch (Exception ignored) { }
        return result;
    }

    public static final ModConfigSpec CONFIG_SPEC = buildConfig();

    private static ModConfigSpec buildConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        terminalKeyboardSounds = builder.define("TerminalKeyboardSounds", terminalKeyboardSoundsDefault);
        if (!FMLLoader.isProduction()) {
            gasPressureOverlay = builder
                    .comment("Debug: tint every gas node with a colour for its pressure,",
                             "blue for empty through to red at the node's ceiling.",
                             "Development environments only; absent from released builds.")
                    .define("GasPressureDebugOverlay", false);
        }
        return builder.build();
    }

    // Server config
    public enum ConverterOverpowerBehavior {
        EXPLODE,
        DESTROY
    }

    public enum ScriptCommandFailureBehavior {
        FAIL_SILENTLY,
        LOG
    }

    private static ModConfigSpec.EnumValue<ConverterOverpowerBehavior> converterOverpowerBehavior;
    private static ModConfigSpec.EnumValue<ScriptCommandFailureBehavior> scriptCommandFailureBehavior;
    private static ModConfigSpec.ConfigValue<List<? extends String>> gasEdgeForeignBlocklist;

    /**
     * Blocks from other mods that author their own Kelvin gas edges. ZPS never creates an edge to
     * one of these, so the two mods cannot fight over the same connection. Everything else gets a
     * plain pipe edge from our side, since most foreign gas blocks author no edges at all and would
     * otherwise never connect to anything of ours.
     */
    private static final List<String> gasEdgeForeignBlocklistDefault = List.of(
            // Clockwork's edge authors: its duct, plus the two IConnectable block entities.
            "vs_clockwork:duct",
            "vs_clockwork:hose_port",
            "vs_clockwork:extendon");

    public static boolean authorsOwnGasEdges(ResourceLocation block) {
        String id = block.toString();
        try {
            return gasEdgeForeignBlocklist.get().contains(id);
        } catch (Exception ignored) { }
        return gasEdgeForeignBlocklistDefault.contains(id);
    }

    public static ConverterOverpowerBehavior getConverterOverpowerBehavior() {
        try {
            return converterOverpowerBehavior.get();
        } catch (Exception ignored) { }
        return ConverterOverpowerBehavior.DESTROY;
    }

    public static ScriptCommandFailureBehavior getScriptCommandFailureBehavior() {
        try {
            return scriptCommandFailureBehavior.get();
        } catch (Exception ignored) { }
        return ScriptCommandFailureBehavior.FAIL_SILENTLY;
    }

    // --- fusion reactor -------------------------------------------------------------------
    // Every value the reactor runs on. All of them are expected to move after in-game testing,
    // which is why they live here rather than as constants.

    private static ModConfigSpec.ConfigValue<Double> reactorIgnitionTemperatureK;
    private static ModConfigSpec.ConfigValue<Double> reactorMeltTemperatureK;
    private static ModConfigSpec.ConfigValue<Double> reactorWallHeatCapacityJPerK;
    private static ModConfigSpec.ConfigValue<Integer> reactorMaxInteriorExtent;
    private static ModConfigSpec.ConfigValue<Integer> exchangerFePerTick;
    private static ModConfigSpec.ConfigValue<Integer> exchangerBufferFe;
    private static ModConfigSpec.ConfigValue<Double> exchangerGenerationFloorK;
    private static ModConfigSpec.ConfigValue<Double> exchangerHeatingCutoffK;
    private static ModConfigSpec.ConfigValue<Double> exhaustKgPerTick;
    private static ModConfigSpec.ConfigValue<Double> exhaustOutletTemperatureK;
    private static ModConfigSpec.ConfigValue<Double> exhaustBackpressureLimitPa;
    private static ModConfigSpec.ConfigValue<Double> burstBasePressurePa;
    private static ModConfigSpec.ConfigValue<Double> burstCompactnessExponent;
    private static ModConfigSpec.ConfigValue<Double> burstSizeExponent;
    private static ModConfigSpec.ConfigValue<Double> burstRadiusPerOvershoot;
    private static ModConfigSpec.ConfigValue<Integer> breachFireRadius;
    private static ModConfigSpec.ConfigValue<Integer> breachIgniteRadius;
    private static ModConfigSpec.ConfigValue<Integer> reactorAdvancementRadius;

    public static final double REACTOR_IGNITION_TEMPERATURE_K_DEFAULT = 50_000.0;
    public static final double REACTOR_MELT_TEMPERATURE_K_DEFAULT = 100_000.0;
    public static final double REACTOR_WALL_HEAT_CAPACITY_DEFAULT = 150.0;
    public static final int REACTOR_MAX_INTERIOR_EXTENT_DEFAULT = 14;
    public static final int EXCHANGER_FE_PER_TICK_DEFAULT = 4096;
    public static final int EXCHANGER_BUFFER_FE_DEFAULT = 16_384;
    public static final double EXCHANGER_GENERATION_FLOOR_K_DEFAULT = 55_000.0;
    public static final double EXCHANGER_HEATING_CUTOFF_K_DEFAULT = 55_000.0;
    public static final double EXHAUST_KG_PER_TICK_DEFAULT = 0.005;
    public static final double EXHAUST_OUTLET_TEMPERATURE_K_DEFAULT = 1000.0;
    public static final double EXHAUST_BACKPRESSURE_LIMIT_PA_DEFAULT = 8_000_000.0;
    public static final double BURST_BASE_PRESSURE_PA_DEFAULT = 24_000_000.0;
    public static final double BURST_COMPACTNESS_EXPONENT_DEFAULT = 2.0;
    public static final double BURST_SIZE_EXPONENT_DEFAULT = 0.1;
    public static final double BURST_RADIUS_PER_OVERSHOOT_DEFAULT = 4.0;
    public static final int BREACH_FIRE_RADIUS_DEFAULT = 2;
    public static final int BREACH_IGNITE_RADIUS_DEFAULT = 4;
    public static final int REACTOR_ADVANCEMENT_RADIUS_DEFAULT = 32;

    private static double doubleOr(ModConfigSpec.ConfigValue<Double> value, double fallback) {
        try {
            return value.get();
        } catch (Exception ignored) { }
        return fallback;
    }

    private static int intOr(ModConfigSpec.ConfigValue<Integer> value, int fallback) {
        try {
            return value.get();
        } catch (Exception ignored) { }
        return fallback;
    }

    public static double reactorIgnitionTemperatureK() {
        return doubleOr(reactorIgnitionTemperatureK, REACTOR_IGNITION_TEMPERATURE_K_DEFAULT);
    }

    public static double reactorMeltTemperatureK() {
        return doubleOr(reactorMeltTemperatureK, REACTOR_MELT_TEMPERATURE_K_DEFAULT);
    }

    public static double reactorWallHeatCapacityJPerK() {
        return doubleOr(reactorWallHeatCapacityJPerK, REACTOR_WALL_HEAT_CAPACITY_DEFAULT);
    }

    public static int reactorMaxInteriorExtent() {
        return intOr(reactorMaxInteriorExtent, REACTOR_MAX_INTERIOR_EXTENT_DEFAULT);
    }

    public static int exchangerFePerTick() {
        return intOr(exchangerFePerTick, EXCHANGER_FE_PER_TICK_DEFAULT);
    }

    public static int exchangerBufferFe() {
        return intOr(exchangerBufferFe, EXCHANGER_BUFFER_FE_DEFAULT);
    }

    public static double exchangerGenerationFloorK() {
        return doubleOr(exchangerGenerationFloorK, EXCHANGER_GENERATION_FLOOR_K_DEFAULT);
    }

    public static double exchangerHeatingCutoffK() {
        return doubleOr(exchangerHeatingCutoffK, EXCHANGER_HEATING_CUTOFF_K_DEFAULT);
    }

    public static double exhaustKgPerTick() {
        return doubleOr(exhaustKgPerTick, EXHAUST_KG_PER_TICK_DEFAULT);
    }

    public static double exhaustOutletTemperatureK() {
        return doubleOr(exhaustOutletTemperatureK, EXHAUST_OUTLET_TEMPERATURE_K_DEFAULT);
    }

    public static double exhaustBackpressureLimitPa() {
        return doubleOr(exhaustBackpressureLimitPa, EXHAUST_BACKPRESSURE_LIMIT_PA_DEFAULT);
    }

    public static double burstBasePressurePa() {
        return doubleOr(burstBasePressurePa, BURST_BASE_PRESSURE_PA_DEFAULT);
    }

    public static double burstCompactnessExponent() {
        return doubleOr(burstCompactnessExponent, BURST_COMPACTNESS_EXPONENT_DEFAULT);
    }

    public static double burstSizeExponent() {
        return doubleOr(burstSizeExponent, BURST_SIZE_EXPONENT_DEFAULT);
    }

    public static double burstRadiusPerOvershoot() {
        return doubleOr(burstRadiusPerOvershoot, BURST_RADIUS_PER_OVERSHOOT_DEFAULT);
    }

    public static int breachFireRadius() {
        return intOr(breachFireRadius, BREACH_FIRE_RADIUS_DEFAULT);
    }

    public static int breachIgniteRadius() {
        return intOr(breachIgniteRadius, BREACH_IGNITE_RADIUS_DEFAULT);
    }

    public static int reactorAdvancementRadius() {
        return intOr(reactorAdvancementRadius, REACTOR_ADVANCEMENT_RADIUS_DEFAULT);
    }

    public static final ModConfigSpec SERVER_CONFIG_SPEC = buildServerConfig();

    private static ModConfigSpec buildServerConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        converterOverpowerBehavior = builder
                .comment("What happens when a Redstone Converter is connected to an energized Step-Up Transformer.",
                         "EXPLODE: destroys the converter with an explosion",
                         "DESTROY: removes the converter and emits smoke particles (default)")
                .defineEnum("ConverterOverpowerBehavior", ConverterOverpowerBehavior.DESTROY);
        scriptCommandFailureBehavior = builder
                .comment("What happens when a script terminal command fails.",
                         "FAIL_SILENTLY: suppresses failure logs and ignores failed commands (default)",
                         "LOG: logs a concise command failure message and ignores the failed command")
                .defineEnum("ScriptCommandFailureBehavior", ScriptCommandFailureBehavior.FAIL_SILENTLY);
        gasEdgeForeignBlocklist = builder
                .comment("Blocks from other mods that create their own Kelvin gas connections.",
                         "ZPS will not create a gas edge to any block in this list, leaving that",
                         "mod free to manage the connection on its own terms. Blocks not listed",
                         "get a plain pipe edge from our side.")
                .defineList("GasEdgeForeignBlocklist",
                        gasEdgeForeignBlocklistDefault,
                        entry -> entry instanceof String id && ResourceLocation.tryParse(id) != null);

        builder.comment("Fusion reactor. Every value here is a starting point and is expected to move.")
                .push("Reactor");
        reactorIgnitionTemperatureK = builder
                .comment("Chamber temperature, in kelvin, at which Flux fuses. Must match the",
                         "kelvin:min_temperature of the zps:flux_fusion reaction.")
                .defineInRange("IgnitionTemperatureK", REACTOR_IGNITION_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        reactorMeltTemperatureK = builder
                .comment("Chamber temperature, in kelvin, at which a wall block gives way.")
                .defineInRange("MeltTemperatureK", REACTOR_MELT_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        reactorWallHeatCapacityJPerK = builder
                .comment("Thermal mass of one wall block, in joules per kelvin. Sets how much FE",
                         "ignition costs and how quickly the chamber heats and cools.")
                .defineInRange("WallHeatCapacityJPerK", REACTOR_WALL_HEAT_CAPACITY_DEFAULT, 0.001, 1.0e9);
        reactorMaxInteriorExtent = builder
                .comment("Largest interior size along any axis, in blocks.")
                .defineInRange("MaxInteriorExtent", REACTOR_MAX_INTERIOR_EXTENT_DEFAULT, 1, 64);
        exchangerFePerTick = builder
                .comment("Heat Exchanger heating draw and generation cap, in FE per tick.")
                .defineInRange("ExchangerFePerTick", EXCHANGER_FE_PER_TICK_DEFAULT, 1, Integer.MAX_VALUE);
        exchangerBufferFe = builder
                .comment("Heat Exchanger internal buffer, in FE.")
                .defineInRange("ExchangerBufferFe", EXCHANGER_BUFFER_FE_DEFAULT, 1, Integer.MAX_VALUE);
        exchangerGenerationFloorK = builder
                .comment("Heat Exchangers stop drawing heat once the chamber is this cold, in kelvin.",
                         "Keep it above the ignition temperature or cold fuel will quench the reactor.")
                .defineInRange("ExchangerGenerationFloorK", EXCHANGER_GENERATION_FLOOR_K_DEFAULT, 1.0, 1.0e9);
        exchangerHeatingCutoffK = builder
                .comment("Heat Exchangers stop accepting FE once the chamber is this hot, in kelvin.")
                .defineInRange("ExchangerHeatingCutoffK", EXCHANGER_HEATING_CUTOFF_K_DEFAULT, 1.0, 1.0e9);
        exhaustKgPerTick = builder
                .comment("Gas one Exhaust Port can draw out of the chamber, in kilograms per tick.")
                .defineInRange("ExhaustKgPerTick", EXHAUST_KG_PER_TICK_DEFAULT, 1.0e-9, 1.0e6);
        exhaustOutletTemperatureK = builder
                .comment("Temperature an Exhaust Port cools released gas to, in kelvin. Keep it",
                         "below what a Gas Duct can carry.")
                .defineInRange("ExhaustOutletTemperatureK", EXHAUST_OUTLET_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        exhaustBackpressureLimitPa = builder
                .comment("An Exhaust Port stops drawing once its own outlet reaches this pressure, in pascals.")
                .defineInRange("ExhaustBackpressureLimitPa", EXHAUST_BACKPRESSURE_LIMIT_PA_DEFAULT, 1.0, 1.0e12);
        burstBasePressurePa = builder
                .comment("Burst pressure of a compact 3x3x3 chamber, in pascals. Less compact and",
                         "larger shapes are rated lower.")
                .defineInRange("BurstBasePressurePa", BURST_BASE_PRESSURE_PA_DEFAULT, 1.0, 1.0e12);
        burstCompactnessExponent = builder
                .comment("How hard poor compactness reduces the burst rating. 0 disables it.")
                .defineInRange("BurstCompactnessExponent", BURST_COMPACTNESS_EXPONENT_DEFAULT, 0.0, 16.0);
        burstSizeExponent = builder
                .comment("How hard size reduces the burst rating. 0 disables it.")
                .defineInRange("BurstSizeExponent", BURST_SIZE_EXPONENT_DEFAULT, 0.0, 4.0);
        burstRadiusPerOvershoot = builder
                .comment("Extra explosion radius per 100% over the burst pressure.")
                .defineInRange("BurstRadiusPerOvershoot", BURST_RADIUS_PER_OVERSHOOT_DEFAULT, 0.0, 32.0);
        breachFireRadius = builder
                .comment("Radius around a melt breach that is set alight, in blocks.")
                .defineInRange("BreachFireRadius", BREACH_FIRE_RADIUS_DEFAULT, 0, 16);
        breachIgniteRadius = builder
                .comment("Radius around a melt breach in which creatures are burned, in blocks.")
                .defineInRange("BreachIgniteRadius", BREACH_IGNITE_RADIUS_DEFAULT, 0, 32);
        reactorAdvancementRadius = builder
                .comment("Players within this many blocks of a reactor earn its advancements.")
                .defineInRange("AdvancementRadius", REACTOR_ADVANCEMENT_RADIUS_DEFAULT, 1, 256);
        builder.pop();
        return builder.build();
    }
}
