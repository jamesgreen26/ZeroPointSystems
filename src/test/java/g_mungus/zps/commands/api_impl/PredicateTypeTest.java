package g_mungus.zps.commands.api_impl;

import org.junit.jupiter.api.Test;

import static g_mungus.zps.commands.api_impl.ZPSScriptCommandSource.PredicateType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the if/unless/else predicate state machine used by conditional
 * executors in {@link g_mungus.zps.commands.api_impl.CommandTreeBuilder}.
 */
public class PredicateTypeTest {

    @Test
    public void none_alwaysPasses() {
        assertTrue(NONE.test(null));
        assertTrue(NONE.test(Boolean.FALSE));
        assertTrue(NONE.test("not even a boolean"));
    }

    @Test
    public void if_passesOnTrueOnly() {
        assertTrue(IF.test(Boolean.TRUE));
        assertFalse(IF.test(Boolean.FALSE));
    }

    @Test
    public void unless_passesOnFalseOnly() {
        assertFalse(UNLESS.test(Boolean.TRUE));
        assertTrue(UNLESS.test(Boolean.FALSE));
    }

    @Test
    public void cycle_invertsConditionForElseBranch() {
        // "else" must evaluate the opposite of the preceding condition
        assertEquals(UNLESS, IF.cycle());
        assertEquals(IF, UNLESS.cycle());
        assertEquals(NONE, NONE.cycle());
    }
}
