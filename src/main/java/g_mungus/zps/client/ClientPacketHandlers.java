package g_mungus.zps.client;

import g_mungus.zps.gas.GasJet;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.networking.AtmosphericCompositionS2CPacket;
import g_mungus.zps.networking.RoomBreachS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

/**
 * Client halves of packets whose handling touches client-only classes. Kept apart from the packets
 * so the dedicated server never has to load them: Forge registers every packet on both sides.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    /** Kilograms of escaping gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.05;
    /** Jets fired per puff, each slower than the last, so the burst trails off rather than ending in a ring. */
    private static final int WAVES = 3;

    public static void roomBreach(RoomBreachS2CPacket packet) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        DuctNodePos node = KelvinExtensions.INSTANCE.toDuctNodePos(packet.pos(), level.dimension().location());
        double particles = packet.massKg() / MASS_PER_PARTICLE / WAVES;
        for (int wave = 0; wave < WAVES; wave++) {
            GasJet.spawn(level, node, ModGases.FLUX, Vec3.atCenterOf(packet.pos()), packet.outward(),
                    0.35 / (wave + 1), Math.max(particles, 4), 0.45, -0.5, packet.buoyancy());
        }
    }

    public static void atmosphericComposition(AtmosphericCompositionS2CPacket packet) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        AtmosphericCompositionS2CPacket.apply(level.registryAccess().registryOrThrow(Registries.BIOME), packet.compositions());
    }
}
