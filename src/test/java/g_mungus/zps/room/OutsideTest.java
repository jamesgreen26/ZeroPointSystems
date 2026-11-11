package g_mungus.zps.room;

import g_mungus.zps.testutil.MinecraftBootstrap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A room's outside atmosphere is its cells' own, added up: the same air everywhere comes to that
 * air for every cell, and a room across two kinds of air leaks toward the mix of them.
 */
public class OutsideTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    private static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath("kelvin", "air");
    private static final ResourceLocation STEAM = ResourceLocation.fromNamespaceAndPath("vs_clockwork", "steam");

    /** Every cell from {@code from} to {@code to} inclusive. */
    private static LongSet box(BlockPos from, BlockPos to) {
        LongSet cells = new LongOpenHashSet();
        BlockPos.betweenClosed(from, to).forEach(pos -> cells.add(pos.asLong()));
        return cells;
    }

    @Test
    void sameAirEverywhere_isThatAirForEveryCell() {
        LongSet room = box(new BlockPos(-3, 61, 5), new BlockPos(5, 66, 9));
        Outside outside = Outside.of(room, at -> Map.of(AIR, 1.2), at -> 293.0);

        assertEquals(1.2 * room.size(), outside.massKg().get(AIR), 1e-9);
        assertEquals(293.0, outside.temperatureK(), 1e-9);
    }

    /** A cave with a quarter of its cells in steam and the rest in vacuum, as cold as vacuum is there. */
    @Test
    void roomAcrossSteamAndVacuum_leaksTowardTheMixOfThem() {
        LongSet room = box(new BlockPos(0, 0, 0), new BlockPos(15, 3, 3));
        Outside outside = Outside.of(room,
                at -> at.getX() < 4 ? Map.of(STEAM, 0.6, AIR, 0.25) : Map.of(),
                at -> at.getX() < 4 ? 273.0 : 201.0);

        int inSteam = room.size() / 4;
        assertEquals(0.6 * inSteam, outside.massKg().get(STEAM), 1e-9);
        assertEquals(0.25 * inSteam, outside.massKg().get(AIR), 1e-9);
        assertEquals((273.0 + 3 * 201.0) / 4, outside.temperatureK(), 1e-9);
    }

    /** Where the room is in the vacuum says nothing: only how much of it is. */
    @Test
    void roomWhollyInVacuum_hasNothingOutside() {
        Outside outside = Outside.of(box(new BlockPos(0, 0, 0), new BlockPos(7, 7, 7)), at -> Map.of(), at -> 201.0);

        assertTrue(outside.massKg().isEmpty());
        assertEquals(201.0, outside.temperatureK(), 1e-9);
    }

    @Test
    void cellsWithNothingExposable_haveNoSayInTheTemperature() {
        LongSet room = box(new BlockPos(0, 0, 0), new BlockPos(7, 3, 3));
        Outside half = Outside.of(room, at -> Map.of(), at -> at.getX() < 4 ? 250.0 : Double.NaN);
        Outside none = Outside.of(room, at -> Map.of(), at -> Double.NaN);

        assertEquals(250.0, half.temperatureK(), 1e-9);
        assertTrue(Double.isNaN(none.temperatureK()));
    }

    /** Cells are counted where they are, not where the room's corner happens to fall. */
    @Test
    void cellsBelowZero_areCountedInTheirOwnBlocks() {
        LongSet room = box(new BlockPos(-4, -4, -4), new BlockPos(3, -1, -1));
        Outside outside = Outside.of(room, at -> at.getX() < 0 ? Map.of(AIR, 1.0) : Map.of(), at -> 293.0);

        assertEquals(room.size() / 2.0, outside.massKg().get(AIR), 1e-9);
    }
}
