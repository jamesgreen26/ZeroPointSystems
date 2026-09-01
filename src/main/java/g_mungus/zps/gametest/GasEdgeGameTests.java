package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * Covers the parts of gas edge negotiation that only show up against a live Kelvin network:
 * that exactly one edge is created per pair however many blocks negotiate it, and that edges are
 * cleaned up when a block goes away — Kelvin's own {@code removeNode} leaves them behind.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasEdgeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos FIRST = new BlockPos(3, 2, 3);
    private static final BlockPos SECOND = new BlockPos(3, 2, 4);

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static void placeDucts(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(SECOND, ModBlocks.GAS_DUCT.get().defaultBlockState());
    }

    /** How many entries in the whole edge map touch this node, under either key ordering. */
    private static long edgesTouching(DuctNodePos target) {
        return kelvin().getEdges().entrySet().stream()
                .filter(entry -> entry.getKey().getFirst().equals(target)
                        || entry.getKey().getSecond().equals(target))
                .count();
    }

    @GameTest(template = TEMPLATE)
    public static void adjacentDuctsFormExactlyOneEdge(GameTestHelper helper) {
        placeDucts(helper);

        DuctNodePos first = node(helper, FIRST);
        DuctNodePos second = node(helper, SECOND);

        DuctEdge edge = kelvin().getEdgeBetween(first, second);
        if (edge == null) {
            helper.fail("No edge was created between two adjacent ducts");
        }

        // Both ducts negotiate the same face independently. Without canonical ordering each would
        // write its own key and Kelvin would end up holding two edges for one connection.
        if (edgesTouching(first) != 1) {
            helper.fail("Expected exactly one edge touching the first duct, found " + edgesTouching(first));
        }
        GasEdgeNegotiator.EdgeKey key = GasEdgeNegotiator.canonical(first, second);
        if (kelvin().getEdges().containsKey(new Pair<>(key.b(), key.a()))) {
            helper.fail("An edge was stored under the reversed key as well as the canonical one");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void breakingADuctRemovesItsEdge(GameTestHelper helper) {
        placeDucts(helper);

        DuctNodePos first = node(helper, FIRST);
        DuctNodePos second = node(helper, SECOND);

        helper.setBlock(SECOND, Blocks.AIR.defaultBlockState());

        if (kelvin().getEdgeBetween(first, second) != null) {
            helper.fail("The edge outlived the duct that was broken");
        }
        if (edgesTouching(second) != 0) {
            helper.fail("Edges are still referencing the removed node");
        }

        // Kelvin keeps edges in a set on the node itself as well as in the network map; a stale
        // entry here would be handed to the solver every tick.
        DuctNode survivor = kelvin().getNodeAt(first);
        if (survivor == null) {
            helper.fail("The surviving duct lost its node");
        } else if (!survivor.getNodeEdges().isEmpty()) {
            helper.fail("The surviving duct kept a stale edge in its node edge set");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void separatedDuctsDoNotConnect(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(SECOND.south(), ModBlocks.GAS_DUCT.get().defaultBlockState());

        if (edgesTouching(node(helper, FIRST)) != 0) {
            helper.fail("Ducts a block apart should not be connected");
        }

        helper.succeed();
    }
}
