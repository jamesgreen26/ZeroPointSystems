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

    private static ForgeConfigSpec.EnumValue<ConverterOverpowerBehavior> converterOverpowerBehavior;

    public static ConverterOverpowerBehavior getConverterOverpowerBehavior() {
        try {
            return converterOverpowerBehavior.get();
        } catch (Exception ignored) { }
        return ConverterOverpowerBehavior.DESTROY;
    }

    public static final ForgeConfigSpec SERVER_CONFIG_SPEC = buildServerConfig();

    private static ForgeConfigSpec buildServerConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        converterOverpowerBehavior = builder
                .comment("What happens when a Redstone Converter is connected to an energized Step-Up Transformer.",
                         "EXPLODE: destroys the converter with an explosion",
                         "DESTROY: removes the converter and emits smoke particles (default)")
                .defineEnum("ConverterOverpowerBehavior", ConverterOverpowerBehavior.DESTROY);
        return builder.build();
    }
}
