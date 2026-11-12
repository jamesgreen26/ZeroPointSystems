package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A room's outside atmosphere is its cells' own, added up: the same air everywhere comes to that
 * air for every cell, and a room across two kinds of air leaks toward the mix of them.
 */
public class OutsideTest {

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

    // --- the gas at one place in a room ------------------------------------------------------

    /** A cave of 100 cells: 10 in steam and air, 90 in vacuum. What its cells hold between them unsealed. */
    private static final Map<ResourceLocation, Double> STEAMY = Map.of(STEAM, 0.6, AIR, 0.2);
    private static final Outside CAVE = new Outside(Map.of(STEAM, 6.0, AIR, 2.0), 210.0);

    /** A room holding exactly its outside is, at every spot, the air that was there anyway. */
    @Test
    void untouchedRoom_isTheOutsideAirAtEverySpot() {
        assertEquals(STEAMY, CAVE.airAt(CAVE.massKg(), 100, STEAMY));
        assertEquals(Map.of(), CAVE.airAt(CAVE.massKg(), 100, Map.of()));
        assertEquals(270.0, CAVE.temperatureAt(210.0, 270.0), 1e-9);
        assertEquals(200.0, CAVE.temperatureAt(210.0, 200.0), 1e-9);
    }

    /** Half the steam gone is half of each spot's own steam: none from where there was none. */
    @Test
    void gasTakenOut_isShortByTheSameShareEverywhere() {
        Map<ResourceLocation, Double> held = Map.of(STEAM, 3.0, AIR, 2.0);

        Map<ResourceLocation, Double> inSteam = CAVE.airAt(held, 100, STEAMY);
        assertEquals(0.3, inSteam.get(STEAM), 1e-9);
        assertEquals(0.2, inSteam.get(AIR), 1e-9);
        assertEquals(Map.of(), CAVE.airAt(held, 100, Map.of()));
    }

    /** Air put in lies evenly over the room, on top of what each spot has of its own. */
    @Test
    void gasPutIn_isSpreadEvenlyOnTop() {
        Map<ResourceLocation, Double> held = Map.of(STEAM, 6.0, AIR, 102.0);

        Map<ResourceLocation, Double> inSteam = CAVE.airAt(held, 100, STEAMY);
        assertEquals(0.6, inSteam.get(STEAM), 1e-9);
        assertEquals(1.2, inSteam.get(AIR), 1e-9);
        assertEquals(Map.of(AIR, 1.0), CAVE.airAt(held, 100, Map.of()));
    }

    /** A gas the outside has none of anywhere is all surplus. */
    @Test
    void foreignGas_isTheSameEverywhere() {
        ResourceLocation flux = ResourceLocation.fromNamespaceAndPath("zps", "flux");
        Map<ResourceLocation, Double> held = Map.of(STEAM, 6.0, AIR, 2.0, flux, 50.0);

        assertEquals(0.5, CAVE.airAt(held, 100, STEAMY).get(flux), 1e-9);
        assertEquals(0.5, CAVE.airAt(held, 100, Map.of()).get(flux), 1e-9);
    }

    /** Whatever has gone in or out, the cells still add up to what the room holds. */
    @Test
    void cells_addUpToWhatTheRoomHolds() {
        Map<ResourceLocation, Double> held = Map.of(STEAM, 1.5, AIR, 40.0);
        Map<ResourceLocation, Double> inSteam = CAVE.airAt(held, 100, STEAMY);
        Map<ResourceLocation, Double> inVacuum = CAVE.airAt(held, 100, Map.of());

        assertEquals(1.5, 10 * inSteam.get(STEAM) + 90 * inVacuum.getOrDefault(STEAM, 0.0), 1e-9);
        assertEquals(40.0, 10 * inSteam.get(AIR) + 90 * inVacuum.get(AIR), 1e-9);
    }

    /** Where the outside is the same all through, the room is one mix: its gas over its volume. */
    @Test
    void uniformOutside_isTheRoomsOneMix() {
        Outside plain = new Outside(Map.of(AIR, 120.0), 293.0);
        Map<ResourceLocation, Double> local = Map.of(AIR, 1.2);

        assertEquals(0.3, plain.airAt(Map.of(AIR, 30.0), 100, local).get(AIR), 1e-9);
        assertEquals(5.0, plain.airAt(Map.of(AIR, 500.0), 100, local).get(AIR), 1e-9);
        assertEquals(400.0, plain.temperatureAt(400.0, 293.0), 1e-9);
    }

    /** A warmed room is warmer by as much at every spot; with nothing to go by, the room's stands. */
    @Test
    void temperature_keepsEachSpotsDistanceFromTheOutside() {
        assertEquals(290.0, CAVE.temperatureAt(230.0, 270.0), 1e-9);
        assertEquals(220.0, CAVE.temperatureAt(230.0, 200.0), 1e-9);
        assertEquals(230.0, CAVE.temperatureAt(230.0, Double.NaN), 1e-9);
        assertEquals(230.0, new Outside(Map.of(), Double.NaN).temperatureAt(230.0, 270.0), 1e-9);
        assertTrue(Double.isNaN(CAVE.temperatureAt(Double.NaN, 270.0)));
        assertEquals(0.0, CAVE.temperatureAt(5.0, 200.0), 1e-9);
    }
}
