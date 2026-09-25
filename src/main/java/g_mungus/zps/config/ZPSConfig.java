package g_mungus.zps.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ZPSConfig {
    // Client config
    private static ForgeConfigSpec.ConfigValue<Boolean> terminalKeyboardSounds;
    private static final boolean terminalKeyboardSoundsDefault = true;

    private static ForgeConfigSpec.ConfigValue<Boolean> reactorGlow;
    private static final boolean reactorGlowDefault = true;

    /** Whether to draw the volumetric glow inside fusion reactors. */
    public static boolean showReactorGlow() {
        boolean result = reactorGlowDefault;
        try {
            result = reactorGlow.get();
        } catch (Exception ignored) { }
        return result;
    }

    private static ForgeConfigSpec.ConfigValue<Boolean> reactorHum;
    private static final boolean reactorHumDefault = true;

    /** Whether a hot fusion reactor hums. */
    public static boolean playReactorHum() {
        boolean result = reactorHumDefault;
        try {
            result = reactorHum.get();
        } catch (Exception ignored) { }
        return result;
    }

    /**
     * Null outside a development environment: the option is not written to the config at all, so
     * it cannot be switched on in a released build.
     */
    private static ForgeConfigSpec.ConfigValue<Boolean> gasPressureOverlay;

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

    public static final ForgeConfigSpec CONFIG_SPEC = buildConfig();

    private static ForgeConfigSpec buildConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        terminalKeyboardSounds = builder.define("TerminalKeyboardSounds", terminalKeyboardSoundsDefault);
        reactorGlow = builder
                .comment("Draw the glowing plasma inside fusion reactors.")
                .define("ReactorGlow", reactorGlowDefault);
        reactorHum = builder
                .comment("Play a hum from hot fusion reactors.")
                .define("ReactorHum", reactorHumDefault);
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

    private static ForgeConfigSpec.EnumValue<ConverterOverpowerBehavior> converterOverpowerBehavior;
    private static ForgeConfigSpec.EnumValue<ScriptCommandFailureBehavior> scriptCommandFailureBehavior;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> gasEdgeForeignBlocklist;
    private static ForgeConfigSpec.ConfigValue<Boolean> ductTravel;
    private static final boolean ductTravelDefault = true;
    private static ForgeConfigSpec.ConfigValue<Integer> ductTravelMaxNodes;
    private static final int ductTravelMaxNodesDefault = 4096;
    private static ForgeConfigSpec.ConfigValue<Double> ductTravelSpeed;
    private static final double ductTravelSpeedDefault = 0.4;
    private static ForgeConfigSpec.ConfigValue<Double> entityBurnGasTemperature;
    private static final double entityBurnGasTemperatureDefault = 850.0;
    private static ForgeConfigSpec.ConfigValue<Double> entityFreezeGasTemperature;
    private static final double entityFreezeGasTemperatureDefault = 250.0;

    /**
     * Blocks from other mods that author their own Kelvin gas edges. ZPS never creates an edge to
     * one of these, so the two mods cannot fight over the same connection. Everything else gets a
     * plain pipe edge from our side, since most foreign gas blocks author no edges at all and would
     * otherwise never connect to anything of ours.
     */
    private static final List<String> gasEdgeForeignBlocklistDefault = List.of("vs_clockwork:duct");

    public static boolean authorsOwnGasEdges(ResourceLocation block) {
        String id = block.toString();
        try {
            return gasEdgeForeignBlocklist.get().contains(id);
        } catch (Exception ignored) { }
        return gasEdgeForeignBlocklistDefault.contains(id);
    }

    /** Whether players can climb into a vent and travel the duct run. */
    public static boolean ductTravelEnabled() {
        try {
            return ductTravel.get();
        } catch (Exception ignored) { }
        return ductTravelDefault;
    }

    /** How many blocks one search for reachable vents will walk before giving up. */
    public static int ductTravelMaxNodes() {
        try {
            return ductTravelMaxNodes.get();
        } catch (Exception ignored) { }
        return ductTravelMaxNodesDefault;
    }

    /** How fast a player crawls between vents, in blocks per tick. */
    public static double ductTravelBlocksPerTick() {
        try {
            return ductTravelSpeed.get();
        } catch (Exception ignored) { }
        return ductTravelSpeedDefault;
    }

    /** Gas this hot, in kelvin, passing the vent a player is sitting in sets them alight. */
    public static double entityBurnGasTemperatureK() {
        try {
            return entityBurnGasTemperature.get();
        } catch (Exception ignored) { }
        return entityBurnGasTemperatureDefault;
    }

    /** Gas this cold, in kelvin, passing the vent a player is sitting in freezes them. */
    public static double entityFreezeGasTemperatureK() {
        try {
            return entityFreezeGasTemperature.get();
        } catch (Exception ignored) { }
        return entityFreezeGasTemperatureDefault;
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
    // The few values a pack maker may want to tune. Everything else the reactor runs on is a
    // constant in g_mungus.zps.reactor.ReactorTuning.

    private static ForgeConfigSpec.ConfigValue<Double> reactorIgnitionTemperatureK;
    private static ForgeConfigSpec.ConfigValue<Double> reactorMeltTemperatureK;
    private static ForgeConfigSpec.ConfigValue<Double> exchangerTemperatureK;
    private static ForgeConfigSpec.ConfigValue<Double> burstPressurePa;
    private static ForgeConfigSpec.ConfigValue<Integer> reactorMaxInteriorExtent;

    public static final double REACTOR_IGNITION_TEMPERATURE_K_DEFAULT = 50_000.0;
    public static final double REACTOR_MELT_TEMPERATURE_K_DEFAULT = 200_000.0;
    public static final double EXCHANGER_TEMPERATURE_K_DEFAULT = 55_000.0;
    public static final double BURST_PRESSURE_PA_DEFAULT = 24_000_000.0;
    public static final int REACTOR_MAX_INTERIOR_EXTENT_DEFAULT = 14;

    private static double doubleOr(ForgeConfigSpec.ConfigValue<Double> value, double fallback) {
        try {
            return value.get();
        } catch (Exception ignored) { }
        return fallback;
    }

    private static int intOr(ForgeConfigSpec.ConfigValue<Integer> value, int fallback) {
        try {
            return value.get();
        } catch (Exception ignored) { }
        return fallback;
    }

    /** Chamber temperature at which Flux fuses, in kelvin. */
    public static double reactorIgnitionTemperatureK() {
        return doubleOr(reactorIgnitionTemperatureK, REACTOR_IGNITION_TEMPERATURE_K_DEFAULT);
    }

    /** Chamber temperature at which a wall block gives way, in kelvin. */
    public static double reactorMeltTemperatureK() {
        return doubleOr(reactorMeltTemperatureK, REACTOR_MELT_TEMPERATURE_K_DEFAULT);
    }

    /**
     * The temperature Heat Exchangers hold the chamber at, in kelvin: they stop pushing FE in
     * once the chamber is this hot and stop drawing heat out once it is this cold.
     */
    public static double exchangerTemperatureK() {
        return doubleOr(exchangerTemperatureK, EXCHANGER_TEMPERATURE_K_DEFAULT);
    }

    /** Pressure at which a reactor chamber bursts, in pascals. */
    public static double burstPressurePa() {
        return doubleOr(burstPressurePa, BURST_PRESSURE_PA_DEFAULT);
    }

    /** Largest interior size along any axis, in blocks. */
    public static int reactorMaxInteriorExtent() {
        return intOr(reactorMaxInteriorExtent, REACTOR_MAX_INTERIOR_EXTENT_DEFAULT);
    }

    // --- energy ---------------------------------------------------------------------------

    private static ForgeConfigSpec.ConfigValue<Integer> combustionGeneratorFePerTick;
    private static ForgeConfigSpec.ConfigValue<Double> exchangerJoulesPerFe;
    private static ForgeConfigSpec.ConfigValue<Integer> standardTransferFePerTick;

    public static final int COMBUSTION_GENERATOR_FE_PER_TICK_DEFAULT = 32;
    public static final double EXCHANGER_JOULES_PER_FE_DEFAULT = 1000.0;
    public static final int STANDARD_TRANSFER_FE_PER_TICK_DEFAULT = 4096;

    /** FE a burning Combustion Generator makes each tick. */
    public static int combustionGeneratorFePerTick() {
        return intOr(combustionGeneratorFePerTick, COMBUSTION_GENERATOR_FE_PER_TICK_DEFAULT);
    }

    /** Joules of chamber heat one FE is worth to a Heat Exchanger, both ways. */
    public static double exchangerJoulesPerFe() {
        return doubleOr(exchangerJoulesPerFe, EXCHANGER_JOULES_PER_FE_DEFAULT);
    }

    /**
     * The FE per tick a single energy link carries: a Heat Exchanger's draw and generation cap,
     * a Step-Up Transformer's throughput, and the rate a Creative Power Cell charges items.
     */
    public static int standardTransferFePerTick() {
        return intOr(standardTransferFePerTick, STANDARD_TRANSFER_FE_PER_TICK_DEFAULT);
    }

    public static final ForgeConfigSpec SERVER_CONFIG_SPEC = buildServerConfig();

    private static ForgeConfigSpec buildServerConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
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

        builder.comment("Fusion reactor.").push("Reactor");
        reactorIgnitionTemperatureK = builder
                .comment("Chamber temperature, in kelvin, at which Flux fuses. Must match the",
                         "kelvin:min_temperature of the zps:flux_fusion reaction.")
                .defineInRange("IgnitionTemperatureK", REACTOR_IGNITION_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        reactorMeltTemperatureK = builder
                .comment("Chamber temperature, in kelvin, at which a wall block gives way.")
                .defineInRange("MeltTemperatureK", REACTOR_MELT_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        exchangerTemperatureK = builder
                .comment("Chamber temperature, in kelvin, that Heat Exchangers hold: they stop pushing",
                         "FE in once the chamber is this hot and stop drawing heat out once it is this",
                         "cold. Keep it above the ignition temperature or cold fuel will quench the reactor.")
                .defineInRange("ExchangerTemperatureK", EXCHANGER_TEMPERATURE_K_DEFAULT, 1.0, 1.0e9);
        burstPressurePa = builder
                .comment("Pressure at which a reactor chamber bursts, in pascals. The same for every shape and size.")
                .defineInRange("BurstPressurePa", BURST_PRESSURE_PA_DEFAULT, 1.0, 1.0e12);
        reactorMaxInteriorExtent = builder
                .comment("Largest interior size along any axis, in blocks.")
                .defineInRange("MaxInteriorExtent", REACTOR_MAX_INTERIOR_EXTENT_DEFAULT, 1, 64);
        builder.pop();

        builder.comment("Energy.").push("Energy");
        combustionGeneratorFePerTick = builder
                .comment("FE a burning Combustion Generator makes each tick.")
                .defineInRange("CombustionGeneratorFePerTick", COMBUSTION_GENERATOR_FE_PER_TICK_DEFAULT, 1, 1_000_000);
        exchangerJoulesPerFe = builder
                .comment("Joules of chamber heat one FE is worth to a Heat Exchanger, both when it",
                         "heats the chamber and when it generates from it.")
                .defineInRange("ExchangerJoulesPerFe", EXCHANGER_JOULES_PER_FE_DEFAULT, 1.0e-3, 1.0e12);
        standardTransferFePerTick = builder
                .comment("FE per tick a single energy link carries: a Heat Exchanger's draw and",
                         "generation cap, a Step-Up Transformer's throughput, and the rate a",
                         "Creative Power Cell charges items. Transformers already placed keep their",
                         "buffer size until they are reloaded.")
                .defineInRange("StandardTransferFePerTick", STANDARD_TRANSFER_FE_PER_TICK_DEFAULT, 1, 1_000_000_000);
        builder.pop();

        ductTravel = builder
                .comment("Whether players can climb into a Vent and travel between every other",
                         "Vent on the same connected duct run.")
                .define("DuctTravel", ductTravelDefault);
        ductTravelMaxNodes = builder
                .comment("How many blocks the search for reachable Vents walks before it gives up.",
                         "Raise it for very large duct networks; lower it if entering a vent stutters.")
                .defineInRange("DuctTravelMaxNodes", ductTravelMaxNodesDefault, 64, 65_536);
        ductTravelSpeed = builder
                .comment("How fast a player crawls between two Vents, in blocks per tick.",
                         "The screen is held black for the length of the journey, so lower values",
                         "make distant Vents a slower way to travel and nearby ones barely a pause.")
                .defineInRange("DuctTravelBlocksPerTick", ductTravelSpeedDefault, 0.05, 64.0);
        entityBurnGasTemperature = builder
                .comment("Gas at or above this temperature, in kelvin, passing the Vent a player is",
                         "sitting in sets them on fire. The ducts are not a safe place to be when",
                         "something hot is being vented through them.")
                .defineInRange("EntityBurnGasTemperature", entityBurnGasTemperatureDefault, 1.0, 1.0e9);
        entityFreezeGasTemperature = builder
                .comment("Gas at or below this temperature, in kelvin, passing the Vent a player is",
                         "sitting in freezes them, as powder snow would.")
                .defineInRange("EntityFreezeGasTemperature", entityFreezeGasTemperatureDefault, 0.0, 1.0e9);
        return builder.build();
    }
}
