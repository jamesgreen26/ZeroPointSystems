package g_mungus.zps.testutil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * 1.20.1's registries refuse to load before {@link Bootstrap} has run, so unit tests that touch
 * blocks, items or dye colours start Minecraft's bootstrap first.
 */
public final class MinecraftBootstrap {
    private MinecraftBootstrap() {
    }

    public static void ensure() {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (RuntimeException | ExceptionInInitializerError e) {
            // Outside FML, Forge's hooks at the end of the bootstrap cannot find their event bus. The
            // vanilla registries are filled by then, which is all these tests need.
        }
    }
}
