package g_mungus.zps.block.gas.core;

import g_mungus.zps.block.gas.core.facets.ApertureFacet;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.block.gas.core.facets.PumpFacet;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.edges.OneWayEdge;
import org.valkyrienskies.kelvin.api.edges.PumpEdge;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The negotiation has to give the same answer computed from either side of a face, since both
 * blocks run it independently. These tests pin down that symmetry along with the facet merge rules.
 */
public class GasEdgeNegotiationTest {

    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    /** Two adjacent nodes; NODE_B is one block south of NODE_A, so nodeA -> nodeB points south. */
    private static final DuctNodePos NODE_A = new DuctNodePos(0, 0, 0, DIMENSION);
    private static final DuctNodePos NODE_B = new DuctNodePos(0, 0, 1, DIMENSION);

    // --- aperture --------------------------------------------------------------------------

    @Test
    void tighterApertureWins() {
        ApertureFacet half = new ApertureFacet(-0.05);
        ApertureFacet shut = new ApertureFacet(-0.125);

        assertEquals(shut, half.mergeWith(shut));
        assertEquals(shut, shut.mergeWith(half), "aperture merge must be commutative");
    }

    // --- pumps -----------------------------------------------------------------------------

    @Test
    void pumpsFacingTheSameWayAddUp() {
        PumpFacet merged = new PumpFacet(1000, Direction.SOUTH)
                .mergeWith(new PumpFacet(500, Direction.SOUTH));

        assertEquals(1500, merged.pressure(), 1e-9);
        assertEquals(Direction.SOUTH, merged.pushDirection());
    }

    @Test
    void opposingPumpsPartlyCancel() {
        PumpFacet merged = new PumpFacet(1000, Direction.SOUTH)
                .mergeWith(new PumpFacet(400, Direction.NORTH));

        assertEquals(600, merged.pressure(), 1e-9);
        assertEquals(Direction.SOUTH, merged.pushDirection(), "the stronger pump should win");
    }

    @Test
    void evenlyOpposedPumpsCancelEntirely() {
        PumpFacet merged = new PumpFacet(1000, Direction.SOUTH)
                .mergeWith(new PumpFacet(1000, Direction.NORTH));

        assertEquals(0, merged.pressure(), 1e-9);
    }

    @Test
    void pumpMergeIsCommutative() {
        PumpFacet strong = new PumpFacet(1000, Direction.SOUTH);
        PumpFacet weak = new PumpFacet(400, Direction.NORTH);

        assertEquals(strong.mergeWith(weak), weak.mergeWith(strong));
    }

    // --- one-way ---------------------------------------------------------------------------

    @Test
    void checkValvesAgreeingStayOpen() {
        OneWayFacet merged = new OneWayFacet(Direction.SOUTH).mergeWith(new OneWayFacet(Direction.SOUTH));

        assertFalse(merged.blocked());
        assertEquals(Direction.SOUTH, merged.allowedDirection());
    }

    @Test
    void checkValvesFacingEachOtherBlockTheConnection() {
        assertTrue(new OneWayFacet(Direction.SOUTH).mergeWith(new OneWayFacet(Direction.NORTH)).blocked());
        assertTrue(new OneWayFacet(Direction.NORTH).mergeWith(new OneWayFacet(Direction.SOUTH)).blocked(),
                "one-way merge must be commutative");
    }

    @Test
    void blockedOneWayShutsTheApertureRatherThanClamping() {
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new OneWayFacet(Direction.SOUTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25).with(new OneWayFacet(Direction.NORTH)))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertTrue(edge.isBlocked());
        assertEquals(-edge.getRadius(), edge.getAperture(), 1e-9,
                "a blocked connection is expressed as a fully closed aperture");
        assertFalse(edge instanceof OneWayEdge,
                "a blocked edge needs no one-way clamp, so it should not implement OneWayEdge");
    }

    // --- edge construction -----------------------------------------------------------------

    @Test
    void plainEdgeDoesNotImplementOneWay() {
        // Kelvin's solver clamps flow on *any* edge implementing OneWayEdge, whatever its state,
        // so an ordinary connection must not implement it at all.
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertFalse(edge instanceof OneWayEdge);
        // Kelvin filters out flow running against a PumpEdge's target whatever the pressure, so a
        // plain duct that implemented PumpEdge would only pass gas toward increasing coordinates.
        assertFalse(edge instanceof PumpEdge,
                "a plain edge must not implement PumpEdge, or it becomes one-directional");
        assertEquals(ConnectionType.PIPE, edge.getType());
    }

    @Test
    void geometryTakesNarrowestBoreAndBothHalfLengths() {
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .mergeWith(GasEdgeProposal.pipe(0.2, 0.3))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertEquals(0.125, edge.getRadius(), 1e-9);
        assertEquals(0.55, edge.getLength(), 1e-9);
    }

    @Test
    void pumpAndValveShareOneEdge() {
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new PumpFacet(1000, Direction.SOUTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25).with(new ApertureFacet(-0.05)))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertInstanceOf(PumpCompositeDuctEdge.class, edge);
        assertEquals(1000, ((PumpCompositeDuctEdge) edge).getPumpPressure(), 1e-9);
        assertEquals(NODE_B, ((PumpCompositeDuctEdge) edge).getTarget(),
                "pushing south means pushing toward nodeB");
        assertEquals(-0.05, edge.getAperture(), 1e-9);
    }

    @Test
    void pumpDirectionSurvivesCanonicalOrdering() {
        // A pump pushing north is pushing from nodeB toward nodeA, given nodeA -> nodeB is south.
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new PumpFacet(1000, Direction.NORTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertEquals(NODE_A, ((PumpCompositeDuctEdge) edge).getTarget());
    }

    @Test
    void oneWayDirectionSurvivesCanonicalOrdering() {
        CompositeDuctEdge southward = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new OneWayFacet(Direction.SOUTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);
        CompositeDuctEdge northward = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new OneWayFacet(Direction.NORTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertInstanceOf(OneWayEdge.class, southward);
        assertFalse(((OneWayEdge) southward).getReversed(), "nodeA -> nodeB is the unreversed sense");
        assertTrue(((OneWayEdge) northward).getReversed());
    }

    // --- interface surface ------------------------------------------------------------------

    /**
     * Kelvin decides behaviour from the interfaces an edge implements, not from their values, so an
     * edge must carry exactly the ones its facets call for and no more. Every combination is
     * checked here because getting this wrong is silent: an unwanted {@code PumpEdge} made every
     * duct one-directional, and an unwanted {@code OneWayEdge} would do the same.
     */
    @Test
    void everyFacetCombinationCarriesExactlyTheInterfacesItNeeds() {
        record Case(String name, GasEdgeProposal proposal, boolean pump, boolean oneWay) {
        }

        GasEdgeProposal plain = GasEdgeProposal.pipe(0.125, 0.25);
        ApertureFacet valve = new ApertureFacet(-0.05);
        PumpFacet pump = new PumpFacet(1000, Direction.SOUTH);
        OneWayFacet check = new OneWayFacet(Direction.SOUTH);

        List<Case> cases = List.of(
                new Case("plain", plain, false, false),
                new Case("aperture", plain.with(valve), false, false),
                new Case("one-way", plain.with(check), false, true),
                new Case("pump", plain.with(pump), true, false),
                new Case("aperture + one-way", plain.with(valve).with(check), false, true),
                new Case("aperture + pump", plain.with(valve).with(pump), true, false),
                // A Kelvin pump already blocks flow against itself, so a check valve pointing the
                // same way adds nothing and the edge stays a pump.
                new Case("pump + one-way", plain.with(pump).with(check), true, false),
                new Case("pump + one-way + aperture",
                        plain.with(pump).with(check).with(valve), true, false));

        for (Case testCase : cases) {
            CompositeDuctEdge edge = testCase.proposal().buildEdge(NODE_A, NODE_B, Direction.SOUTH);

            assertEquals(testCase.pump(), edge instanceof PumpEdge,
                    testCase.name() + ": wrong PumpEdge presence");
            assertEquals(testCase.oneWay(), edge instanceof OneWayEdge,
                    testCase.name() + ": wrong OneWayEdge presence");
        }
    }

    @Test
    void twoOpposedCheckValvesCarryNoDirectionalInterfaceAtAll() {
        // They cancel out into a shut aperture, which needs no clamp of its own.
        CompositeDuctEdge edge = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new OneWayFacet(Direction.SOUTH))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25).with(new OneWayFacet(Direction.NORTH)))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertFalse(edge instanceof OneWayEdge);
        assertFalse(edge instanceof PumpEdge);
        assertTrue(edge.isBlocked());
    }

    // --- symmetry --------------------------------------------------------------------------

    @Test
    void mergingIsSymmetricAcrossTheFace() {
        GasEdgeProposal pump = GasEdgeProposal.pipe(0.125, 0.25).with(new PumpFacet(1000, Direction.SOUTH));
        GasEdgeProposal valve = GasEdgeProposal.pipe(0.2, 0.3).with(new ApertureFacet(-0.05));

        CompositeDuctEdge fromOneSide = pump.mergeWith(valve).buildEdge(NODE_A, NODE_B, Direction.SOUTH);
        CompositeDuctEdge fromTheOther = valve.mergeWith(pump).buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertTrue(fromOneSide.matches(fromTheOther),
                "both blocks must build an identical edge, or they will fight over it");
    }

    @Test
    void canonicalOrderingIgnoresArgumentOrder() {
        assertEquals(GasEdgeNegotiator.canonical(NODE_A, NODE_B),
                GasEdgeNegotiator.canonical(NODE_B, NODE_A));
        assertEquals(NODE_A, GasEdgeNegotiator.canonical(NODE_B, NODE_A).a());
    }

    @Test
    void canonicalOrderingSeparatesDimensions() {
        DuctNodePos nether = new DuctNodePos(0, 0, 0,
                ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"));

        assertEquals(GasEdgeNegotiator.canonical(NODE_A, nether),
                GasEdgeNegotiator.canonical(nether, NODE_A));
    }

    // --- serialization ---------------------------------------------------------------------

    @Test
    void anEdgeWithNoPumpFacetCarriesNoPumpInterface() {
        CompositeDuctEdge valveOnly = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new ApertureFacet(-0.05))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        assertFalse(valveOnly instanceof PumpEdge);
    }

    @Test
    void edgeRoundTripsThroughNbt() {
        CompositeDuctEdge original = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new PumpFacet(1000, Direction.NORTH))
                .with(new ApertureFacet(-0.05))
                .mergeWith(GasEdgeProposal.pipe(0.125, 0.25))
                .buildEdge(NODE_A, NODE_B, Direction.SOUTH);

        CompositeDuctEdge restored =
                new PumpCompositeDuctEdge(original.getType(), NODE_A, NODE_B, 0, 0);
        restored.deserialize(original.serialize(new CompoundTag()));

        assertTrue(original.matches(restored));
    }
}
