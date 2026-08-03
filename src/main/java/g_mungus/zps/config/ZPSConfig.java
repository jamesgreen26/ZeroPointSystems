package g_mungus.zps.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ZPSConfig {
    // Client config
    private static ModConfigSpec.ConfigValue<Boolean> terminalKeyboardSounds;
    private static final boolean terminalKeyboardSoundsDefault = true;

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
        return builder.build();
    }
}
