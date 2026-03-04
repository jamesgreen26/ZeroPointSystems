package g_mungus.zps.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ZPSConfig {
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
}
