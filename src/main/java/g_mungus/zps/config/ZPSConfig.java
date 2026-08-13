package g_mungus.zps.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ZPSConfig {
    // Client config
    private static ForgeConfigSpec.ConfigValue<Boolean> terminalKeyboardSounds;
    private static final boolean terminalKeyboardSoundsDefault = true;

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
        return builder.build();
    }
}
