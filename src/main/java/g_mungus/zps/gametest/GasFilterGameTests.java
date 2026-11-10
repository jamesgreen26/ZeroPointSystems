package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.CompositeDuctEdge;
import g_mungus.zps.block.gas.core.FilteredCompositeDuctEdge;
import g_mungus.zps.block.gas.core.FilteredOneWayCompositeDuctEdge;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.GasEdgeProposal;
import g_mungus.zps.block.gas.core.facets.GasFilterFacet;
import g_mungus.zps.block.gas.core.facets.OneWayFacet;
import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import g_mungus.zps.gas.GasFilter;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.edges.FilteredEdge;

import java.util.Set;

/**
 * The gas filter: a duct that holds the gases on its list back on every face, in both directions,
 * and plain duct joints while the list is empty.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasFilterGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos NORTH_DUCT = new BlockPos(3, 2, 2);
    private static final BlockPos FILTER = new BlockPos(3, 2, 3);
    private static final BlockPos SOUTH_DUCT = new BlockPos(3, 2, 4);

    /** Anything under this is rounding, not gas that got through. */
    private static final double TRACE = 1e-9;

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static GasFilter blocking(GasType gas) {
        return new GasFilter(Set.of(gas.getResourceLocation()));
    }

    private static void placeRun(GameTestHelper helper) {
        helper.setBlock(NORTH_DUCT, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(FILTER, ModBlocks.GAS_FILTER.get().defaultBlockState());
        helper.setBlock(SOUTH_DUCT, ModBlocks.GAS_DUCT.get().defaultBlockState());
    }

    private static GasFilterBlockEntity filterAt(GameTestHelper helper) {
        if (!(helper.getBlockEntity(FILTER) instanceof GasFilterBlockEntity filter)) {
            throw new IllegalStateException("No gas filter block entity");
        }
        return filter;
    }

    private static DuctEdge edge(GameTestHelper helper, BlockPos duct) {
        return kelvin().getEdgeBetween(node(helper, duct), node(helper, FILTER));
    }

    private static double massOf(GameTestHelper helper, BlockPos pos, GasType gas) {
        Double mass = kelvin().getGasMassAt(node(helper, pos)).get(gas);
        return mass == null ? 0.0 : mass;
    }

    @GameTest(template = TEMPLATE)
    public static void emptyFilterFormsPlainEdges(GameTestHelper helper) {
        placeRun(helper);

        for (BlockPos duct : new BlockPos[]{NORTH_DUCT, SOUTH_DUCT}) {
            DuctEdge edge = edge(helper, duct);
            if (edge == null) {
                helper.fail("The filter did not connect to the duct at " + duct);
            } else if (edge.getClass() != CompositeDuctEdge.class) {
                helper.fail("An empty filter should leave a plain edge, found " + edge.getClass().getSimpleName());
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void settingTheFilterRebuildsEveryFace(GameTestHelper helper) {
        placeRun(helper);
        GasFilterBlockEntity filter = filterAt(helper);

        filter.setFilter(blocking(ModGases.STEAM));
        for (BlockPos duct : new BlockPos[]{NORTH_DUCT, SOUTH_DUCT}) {
            DuctEdge edge = edge(helper, duct);
            if (!(edge instanceof FilteredCompositeDuctEdge filtered)) {
                helper.fail("Expected a filtered edge to " + duct + ", found " + edge);
                return;
            }
            if (!filtered.getBlacklist() || !filtered.getFilter().equals(Set.of(ModGases.STEAM))) {
                helper.fail("The edge to " + duct + " does not hold back exactly steam");
            }
        }

        filter.setFilter(GasFilter.PASS_ALL);
        for (BlockPos duct : new BlockPos[]{NORTH_DUCT, SOUTH_DUCT}) {
            DuctEdge edge = edge(helper, duct);
            if (edge == null || edge.getClass() != CompositeDuctEdge.class) {
                helper.fail("Clearing the filter should restore a plain edge to " + duct + ", found " + edge);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockedGasStaysOnTheNorthSide(GameTestHelper helper) {
        holdsBackSteamFlowingFrom(helper, NORTH_DUCT, SOUTH_DUCT);
    }

    /** No direction of its own: the same filter holds back gas coming the other way. */
    @GameTest(template = TEMPLATE)
    public static void blockedGasStaysOnTheSouthSide(GameTestHelper helper) {
        holdsBackSteamFlowingFrom(helper, SOUTH_DUCT, NORTH_DUCT);
    }

    private static void holdsBackSteamFlowingFrom(GameTestHelper helper, BlockPos from, BlockPos to) {
        placeRun(helper);
        filterAt(helper).setFilter(blocking(ModGases.STEAM));

        kelvin().addGasAtTemperature(node(helper, from), ModGases.FLUX, 1.0, 400.0);
        kelvin().addGasAtTemperature(node(helper, from), ModGases.STEAM, 1.0, 400.0);

        helper.runAfterDelay(40, () -> {
            if (massOf(helper, to, ModGases.FLUX) <= TRACE) {
                helper.fail("Flux, which the filter passes, never reached the far duct");
            }
            if (massOf(helper, to, ModGases.STEAM) > TRACE) {
                helper.fail("Steam got past the filter: " + massOf(helper, to, ModGases.STEAM) + " kg");
            }
            if (massOf(helper, FILTER, ModGases.STEAM) > TRACE) {
                helper.fail("Steam got into the filter itself");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void filterSurvivesSaveAndLoad(GameTestHelper helper) {
        placeRun(helper);
        GasFilterBlockEntity filter = filterAt(helper);
        filter.setFilter(blocking(ModGases.STEAM));

        CompoundTag saved = filter.saveWithoutMetadata(helper.getLevel().registryAccess());
        GasFilterBlockEntity reloaded = new GasFilterBlockEntity(filter.getBlockPos(), filter.getBlockState());
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        if (!reloaded.getFilter().equals(filter.getFilter())) {
            helper.fail("The filter came back as " + reloaded.getFilter());
        }
        helper.succeed();
    }

    /** A filter against a check valve keeps both: the merged edge is one-way and filtered. */
    @GameTest(template = TEMPLATE)
    public static void filterCombinesWithACheckValve(GameTestHelper helper) {
        DuctNodePos a = node(helper, FILTER);
        DuctNodePos b = node(helper, SOUTH_DUCT);
        GasEdgeProposal valve = GasEdgeProposal.pipe(0.125, 0.25).with(new OneWayFacet(Direction.SOUTH));
        GasEdgeProposal filter = GasEdgeProposal.pipe(0.125, 0.25)
                .with(new GasFilterFacet(blocking(ModGases.STEAM)));

        CompositeDuctEdge edge = valve.mergeWith(filter).buildEdge(a, b, Direction.SOUTH);
        if (!(edge instanceof FilteredOneWayCompositeDuctEdge oneWay)) {
            helper.fail("Expected a filtered one-way edge, found " + edge.getClass().getSimpleName());
            return;
        }
        if (oneWay.getReversed() || !oneWay.getFilter().equals(Set.of(ModGases.STEAM))) {
            helper.fail("The merged edge lost its direction or its filter");
        }
        helper.succeed();
    }

    /** Two filters on one connection hold back everything either names. */
    @GameTest(template = TEMPLATE)
    public static void neighbouringFiltersHoldBackBothLists(GameTestHelper helper) {
        GasEdgeProposal first = GasEdgeProposal.pipe(0.125, 0.25).with(new GasFilterFacet(blocking(ModGases.STEAM)));
        GasEdgeProposal second = GasEdgeProposal.pipe(0.125, 0.25).with(new GasFilterFacet(blocking(ModGases.FLUX)));

        CompositeDuctEdge edge = first.mergeWith(second)
                .buildEdge(node(helper, FILTER), node(helper, SOUTH_DUCT), Direction.SOUTH);
        if (!(edge instanceof FilteredEdge filtered)
                || !filtered.getFilter().equals(Set.of(ModGases.STEAM, ModGases.FLUX))) {
            helper.fail("Expected steam and flux both held back, found " + edge);
            return;
        }
        helper.succeed();
    }
}
