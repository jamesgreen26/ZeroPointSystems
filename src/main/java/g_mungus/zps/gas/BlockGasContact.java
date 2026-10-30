package g_mungus.zps.gas;

import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.room.RoomScanner;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The gas a block is exposed to: whatever is in its own cell, if anything of it holds air, and in
 * each cell beside it that does. A face against something solid is not exposed.
 *
 * <p>Each cell is read as an entity's eyes are ({@link AtmosphereExposure#atmosphere}): the sealed
 * room it is in, otherwise the biome's atmosphere where it is in the world. Any gas can be in a
 * biome's atmosphere — other mods give biomes their own — so the outside counts as much as a room.
 *
 * <p>For block interactions with gas, each hooked where vanilla already decides that block's
 * fate: steam hydrating farmland, say. Server side.
 */
public final class BlockGasContact {

    /**
     * One body of gas touching a block: its gases, as weights by mass in a room or fractions
     * outside, and its temperature in kelvin, NaN where there is nothing to carry one.
     */
    public record Contact(Map<ResourceLocation, Double> gases, double temperatureK) {
    }

    private BlockGasContact() {
    }

    /** Every body of gas touching the block at {@code pos}; a room touching it on several faces counts once. */
    public static List<Contact> contacts(ServerLevel level, BlockPos pos) {
        List<Contact> contacts = new ArrayList<>();
        List<Room> rooms = new ArrayList<>(2);
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int i = -1; i < Direction.values().length; i++) {
            cell.setWithOffset(pos, i < 0 ? BlockPos.ZERO : Direction.values()[i].getNormal());
            Room room = RoomManager.get(level).roomAt(cell);
            if (room != null) {
                if (!rooms.contains(room)) {
                    rooms.add(room);
                    contacts.add(new Contact(room.gases(), room.temperatureK()));
                }
                continue;
            }
            Contact outside = outside(level, cell);
            if (outside != null) {
                contacts.add(outside);
            }
        }
        return contacts;
    }

    /** Whether any of {@code gas} is touching the block at {@code pos}. Traces in a room do not count. */
    public static boolean touches(ServerLevel level, BlockPos pos, ResourceLocation gas) {
        RoomManager manager = RoomManager.get(level);
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int i = -1; i < Direction.values().length; i++) {
            cell.setWithOffset(pos, i < 0 ? BlockPos.ZERO : Direction.values()[i].getNormal());
            Room room = manager.roomAt(cell);
            if (room != null) {
                if (room.gases().containsKey(gas)) {
                    return true;
                }
                continue;
            }
            if (holdsAir(level, cell) && outsideGases(level, cell).getOrDefault(gas, 0.0) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The outside atmosphere in {@code cell}, or null if no air can be in it. */
    private static @Nullable Contact outside(ServerLevel level, BlockPos cell) {
        if (!holdsAir(level, cell)) {
            return null;
        }
        BlockPos air = AtmosphereExposure.inWorld(level, cell);
        Biome biome = level.getBiome(air).value();
        AtmosphericComposition composition = AtmosphericComposition.of(biome);
        return new Contact(composition.at(air.getY()), AtmosphereExposure.temperatureK(biome, composition, air));
    }

    private static Map<ResourceLocation, Double> outsideGases(ServerLevel level, BlockPos cell) {
        BlockPos air = AtmosphereExposure.inWorld(level, cell);
        return AtmosphericComposition.of(level.getBiome(air).value()).at(air.getY());
    }

    private static boolean holdsAir(ServerLevel level, BlockPos cell) {
        return level.isLoaded(cell) && RoomScanner.holdsAir(RoomScanner.shapeOf(level, cell, level.getBlockState(cell)));
    }
}
