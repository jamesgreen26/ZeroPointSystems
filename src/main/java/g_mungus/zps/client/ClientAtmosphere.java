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
    /**
     * Kept whole, never packed with {@link BlockPos#asLong}: that holds only 12 bits of height, so the
     * camera above y 2047 would come back wrapped round to the bottom of the world, at full density.
     */
    private static BlockPos cachedPos;
    private static float cachedDensity = 1.0f;

    private ClientAtmosphere() {
    }

    public static float cameraDensity(ClientLevel level) {
        BlockPos pos = BlockPos.containing(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (level != cachedLevel || !pos.equals(cachedPos)) {
            cachedLevel = level;
            cachedPos = pos;
            cachedDensity = AtmosphericComposition.getDensity(level, pos);
        }
        return cachedDensity;
    }

    /** How much of the weather is seen from the camera: all of it at density 1, none at {@link #WEATHERLESS_DENSITY} or below. */
    public static float weather(ClientLevel level) {
        return Mth.clamp((cameraDensity(level) - WEATHERLESS_DENSITY) / (1.0f - WEATHERLESS_DENSITY), 0.0f, 1.0f);
    }
}
