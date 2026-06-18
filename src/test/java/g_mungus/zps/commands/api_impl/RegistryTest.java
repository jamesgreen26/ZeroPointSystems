package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Registry} lifecycle across repeated registration rounds.
 *
 * RegisterCommandsEvent fires on every server start AND on /reload, and the
 * registration sets are static. ScriptMapper2 wraps its function in a capturing
 * lambda, so two structurally identical registrations are never equal() — without
 * clearing between rounds, duplicates accumulate and produce duplicate command
 * tree branches.
 */
public class RegistryTest {

    private static final ResourceLocation IN = ResourceLocation.parse("zps:a");
    private static final ResourceLocation OUT = ResourceLocation.parse("zps:b");

    private static ScriptMapper2<Integer, Integer, Integer> identicalMapper2() {
        return new ScriptMapper2<>(
                "plus",
                Integer.class,
                Integer.class,
                IN,
                OUT,
                "int",
                (value, ctx) -> value + ctx.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                IN
        );
    }

    @AfterEach
    void cleanup() {
        Registry.clear();
    }

    @Test
    public void identicalMapper2RegistrationsAreNotDeduplicated() {
        // Documents WHY Registry.clear() is required between registration rounds:
        // ScriptMapper2's constructor wraps the function in a fresh capturing lambda,
        // so value-based dedup in the HashSet can never kick in.
        Registry.register(identicalMapper2());
        Registry.register(identicalMapper2());

        assertEquals(2, Registry.MAPPERS.size(),
                "Structurally identical ScriptMapper2 instances are distinct; "
                        + "re-registration without clear() accumulates duplicates");
    }

    @Test
    public void clearEmptiesAllRegistries() {
        Registry.register(identicalMapper2());
        Registry.register(new ScriptMapper<>(
                "x", Integer.class, Integer.class, IN, OUT, (v, ctx) -> v));
        assertFalse(Registry.MAPPERS.isEmpty());

        Registry.clear();

        assertTrue(Registry.MAPPERS.isEmpty());
        assertTrue(Registry.GETTERS.isEmpty());
        assertTrue(Registry.EXECUTORS.isEmpty());
    }

    @Test
    public void clearThenReregisterKeepsRegistrySizeStable() {
        // Simulates two RegisterCommandsEvent rounds (server start, then /reload)
        for (int round = 0; round < 2; round++) {
            Registry.clear();
            Registry.register(identicalMapper2());
        }
        assertEquals(1, Registry.MAPPERS.size(),
                "With clear() per round, repeated registration must not grow the registry");
    }
}
