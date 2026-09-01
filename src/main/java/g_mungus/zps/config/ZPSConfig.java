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
        return builder.build();
    }
}
