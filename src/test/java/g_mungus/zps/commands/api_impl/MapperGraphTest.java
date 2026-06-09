package g_mungus.zps.commands.api_impl;

import g_mungus.zps.commands.api.ScriptMapper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MapperGraph} transitive-closure computation.
 *
 * Uses plain String-typed mappers; the graph only inspects input/output keys.
 */
public class MapperGraphTest {

    private static final ResourceLocation A = ResourceLocation.parse("zps:a");
    private static final ResourceLocation B = ResourceLocation.parse("zps:b");
    private static final ResourceLocation C = ResourceLocation.parse("zps:c");
    private static final ResourceLocation D = ResourceLocation.parse("zps:d");

    private static ScriptMapper<String, String> mapper(String name, ResourceLocation in, ResourceLocation out) {
        return new ScriptMapper<>(name, String.class, String.class, in, out, (s, ctx) -> s);
    }

    @Test
    public void directProducerIsFound() {
        MapperGraph graph = new MapperGraph();
        ScriptMapper<String, String> aToB = mapper("a_to_b", A, B);
        graph.addMapper(aToB);

        Set<ScriptMapper<?, ?>> result = graph.findAllMappersLeadingTo(B);
        assertEquals(Set.of(aToB), result);
    }

    @Test
    public void transitiveProducersAreIncluded() {
        MapperGraph graph = new MapperGraph();
        ScriptMapper<String, String> aToB = mapper("a_to_b", A, B);
        ScriptMapper<String, String> bToC = mapper("b_to_c", B, C);
        graph.addMapper(aToB);
        graph.addMapper(bToC);

        Set<ScriptMapper<?, ?>> result = graph.findAllMappersLeadingTo(C);
        assertEquals(Set.of(aToB, bToC), result,
                "Closure for C should include the chain A -> B -> C");
    }

    @Test
    public void unrelatedBranchesAreExcluded() {
        MapperGraph graph = new MapperGraph();
        ScriptMapper<String, String> aToB = mapper("a_to_b", A, B);
        ScriptMapper<String, String> cToD = mapper("c_to_d", C, D);
        graph.addMapper(aToB);
        graph.addMapper(cToD);

        assertEquals(Set.of(aToB), graph.findAllMappersLeadingTo(B));
        assertEquals(Set.of(cToD), graph.findAllMappersLeadingTo(D));
    }

    @Test
    public void unknownOutputReturnsEmptySet() {
        MapperGraph graph = new MapperGraph();
        graph.addMapper(mapper("a_to_b", A, B));

        assertTrue(graph.findAllMappersLeadingTo(D).isEmpty());
    }

    @Test
    public void emptyGraphReturnsEmptySet() {
        MapperGraph graph = new MapperGraph();
        assertTrue(graph.findAllMappersLeadingTo(A).isEmpty());
    }

    @Test
    public void selfLoopTerminatesAndIsIncluded() {
        // e.g. offset_x: block_pos -> block_pos
        MapperGraph graph = new MapperGraph();
        ScriptMapper<String, String> aToA = mapper("self", A, A);
        graph.addMapper(aToA);

        Set<ScriptMapper<?, ?>> result = graph.findAllMappersLeadingTo(A);
        assertEquals(Set.of(aToA), result);
    }

    @Test
    public void twoNodeCycleTerminatesAndIncludesBothDirections() {
        // e.g. int -> double ("*") and double -> int ("rounded_down")
        MapperGraph graph = new MapperGraph();
        ScriptMapper<String, String> aToB = mapper("a_to_b", A, B);
        ScriptMapper<String, String> bToA = mapper("b_to_a", B, A);
        graph.addMapper(aToB);
        graph.addMapper(bToA);

        assertEquals(Set.of(aToB, bToA), graph.findAllMappersLeadingTo(A));
        assertEquals(Set.of(aToB, bToA), graph.findAllMappersLeadingTo(B));
    }

    @Test
    public void cacheIsInvalidatedWhenMapperAddedAfterQuery() {
        MapperGraph graph = new MapperGraph();
        graph.addMapper(mapper("a_to_b", A, B));

        // Prime the cache
        assertEquals(1, graph.findAllMappersLeadingTo(B).size());

        // Adding a new producer afterwards must be reflected in later queries
        ScriptMapper<String, String> cToA = mapper("c_to_a", C, A);
        graph.addMapper(cToA);

        Set<ScriptMapper<?, ?>> result = graph.findAllMappersLeadingTo(B);
        assertEquals(2, result.size(), "Cache should be rebuilt after addMapper");
        assertTrue(result.contains(cToA));
    }
}
