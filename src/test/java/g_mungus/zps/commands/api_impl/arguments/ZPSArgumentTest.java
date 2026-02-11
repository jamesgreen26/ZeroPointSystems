package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ZPSArgument to verify it properly distinguishes argument nodes
 * with the same name but different redirects using equals/hashCode
 */
public class ZPSArgumentTest {

    @Test
    public void testArgumentsWithSameNameButDifferentRedirectsAreNotEqual() {
        // Create target nodes
        ZPSLiteral<SharedSuggestionProvider> target1 = new ZPSLiteral.Builder<SharedSuggestionProvider>("target1").build();
        ZPSLiteral<SharedSuggestionProvider> target2 = new ZPSLiteral.Builder<SharedSuggestionProvider>("target2").build();

        // Create two argument nodes with same name but different redirects
        ZPSArgument<SharedSuggestionProvider, Integer> arg1 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target1)
                .build();

        ZPSArgument<SharedSuggestionProvider, Integer> arg2 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target2)
                .build();

        // Verify they are not equal
        assertNotEquals(arg1, arg2, "Arguments with same name but different redirects should NOT be equal");
        assertNotEquals(arg1.hashCode(), arg2.hashCode(), "Arguments with different redirects should have different hash codes");
    }

    @Test
    public void testHashMapPreservesArgumentsWithDifferentRedirects() {
        // Create target nodes
        ZPSLiteral<SharedSuggestionProvider> target1 = new ZPSLiteral.Builder<SharedSuggestionProvider>("target1").build();
        ZPSLiteral<SharedSuggestionProvider> target2 = new ZPSLiteral.Builder<SharedSuggestionProvider>("target2").build();

        // Create two argument nodes with same name but different redirects
        ZPSArgument<SharedSuggestionProvider, Integer> arg1 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target1)
                .build();

        ZPSArgument<SharedSuggestionProvider, Integer> arg2 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target2)
                .build();

        // Test with regular HashMap - should preserve both nodes now
        Map<ZPSArgument<SharedSuggestionProvider, Integer>, String> map = new HashMap<>();
        map.put(arg1, "first");
        map.put(arg2, "second");

        System.out.println("\n=== HashMap with ZPSArgument ===");
        System.out.println("Map size: " + map.size());
        System.out.println("arg1 maps to: " + map.get(arg1));
        System.out.println("arg2 maps to: " + map.get(arg2));

        // With ZPSArgument's redirect-aware equals/hashCode, regular HashMap works correctly
        assertEquals(2, map.size(), "HashMap should preserve both arguments with ZPSArgument");
        assertEquals("first", map.get(arg1), "arg1 should map to 'first'");
        assertEquals("second", map.get(arg2), "arg2 should map to 'second'");

        System.out.println("\n✓ HashMap with ZPSArgument correctly preserves arguments with different redirects");
    }

    @Test
    public void testArgumentsWithSameNameAndSameRedirectAreEqual() {
        // Create target node
        ZPSLiteral<SharedSuggestionProvider> target = new ZPSLiteral.Builder<SharedSuggestionProvider>("target").build();

        // Create two argument nodes with same name and same redirect
        ZPSArgument<SharedSuggestionProvider, Integer> arg1 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target)
                .build();

        ZPSArgument<SharedSuggestionProvider, Integer> arg2 = ZPSArgument.Builder
                .<SharedSuggestionProvider, Integer>argument("value", IntegerArgumentType.integer())
                .redirect(target)
                .build();

        // Verify they ARE equal (allowing beneficial deduplication)
        assertEquals(arg1, arg2, "Arguments with same name and same redirect should be equal");
        assertEquals(arg1.hashCode(), arg2.hashCode(), "Arguments with same redirect should have same hash code");

        // HashMap should deduplicate them
        Map<ZPSArgument<SharedSuggestionProvider, Integer>, String> map = new HashMap<>();
        map.put(arg1, "first");
        map.put(arg2, "second");

        assertEquals(1, map.size(), "HashMap should deduplicate arguments with same name and redirect");
        assertEquals("second", map.get(arg1), "Both keys should map to last value");
        assertEquals("second", map.get(arg2), "Both keys should map to last value");

        System.out.println("\n✓ ZPSArgument allows beneficial deduplication for arguments with same name and redirect");
    }
}
