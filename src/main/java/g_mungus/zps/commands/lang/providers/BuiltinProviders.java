package g_mungus.zps.commands.lang.providers;

import g_mungus.zps.commands.ZPSCommands;
import net.minecraft.core.BlockPos;

public class BuiltinProviders {
    public static void register() {
        ProviderRegistry.registerWithDerivatives(
                BlockPos.class,
                "POS",
                ZPSCommands::getPosition,
                Integer.class,
                "X", "Y", "Z"
        );

    }
}
