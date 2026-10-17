package g_mungus.zps.client;

import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * The air where the camera is, for the sky mixins: its {@link AtmosphericComposition#getDensity density},
 * and how much of the weather is left at that density. The density is asked for many times a frame,
 * so it is kept until the camera moves to another block or level.
 */
public final class ClientAtmosphere {
    /** Below this density there is no weather left to see. */
    public static final float WEATHERLESS_DENSITY = 0.8f;

    private static ClientLevel cachedLevel;
    private static long cachedPos;
    private static float cachedDensity = 1.0f;

    private ClientAtmosphere() {
    }

    public static float cameraDensity(ClientLevel level) {
        long pos = BlockPos.containing(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()).asLong();
        if (level != cachedLevel || pos != cachedPos) {
            cachedLevel = level;
            cachedPos = pos;
            cachedDensity = AtmosphericComposition.getDensity(level, BlockPos.of(pos));
        }
        return cachedDensity;
    }

    /** How much of the weather is seen from the camera: all of it at density 1, none at {@link #WEATHERLESS_DENSITY} or below. */
    public static float weather(ClientLevel level) {
        return Mth.clamp((cameraDensity(level) - WEATHERLESS_DENSITY) / (1.0f - WEATHERLESS_DENSITY), 0.0f, 1.0f);
    }
}
