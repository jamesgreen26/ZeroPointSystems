package g_mungus.zps.commands.lang.comparators;

import net.minecraft.core.BlockPos;

public final class BuiltinComparisons {

    public static void register() {

        // Integer comparisons
        ComparisonRegistry.register(Integer.class, "EQUALS", Integer::equals);
        ComparisonRegistry.register(Integer.class, "GREATER_THAN", (a, b) -> a > b);
        ComparisonRegistry.register(Integer.class, "LESS_THAN", (a, b) -> a < b);

        // Double comparisons
        ComparisonRegistry.register(Double.class, "EQUALS", Double::equals);
        ComparisonRegistry.register(Double.class, "GREATER_THAN", (a, b) -> a > b);
        ComparisonRegistry.register(Double.class, "LESS_THAN", (a, b) -> a < b);

        // BlockPos Comparisons
        ComparisonRegistry.register(BlockPos.class, "EQUALS", BlockPos::equals);
    }

    private BuiltinComparisons() {}
}
