package g_mungus.zps.commands.lang.v2.classes;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;
import net.minecraft.core.BlockPos;

import java.util.List;

public record DoubleClass(String name, Double value) implements ScriptClass<Double> {

    @Override
    public Class<Double> getType() {
        return Double.class;
    }

    @Override
    public MappedArgumentType<?, Double> getArgumentType() {
        return MappedArgumentType.simple(DoubleArgumentType.doubleArg(), Double.class);
    }

    @Override
    public List<ScriptComparator<Double>> getComparators() {
        return List.of(
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "EQUALS";
                    }

                    @Override
                    public boolean compare(Double left, Double right) {
                        return left.equals(right);
                    }
                },
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "LESS_THAN";
                    }

                    @Override
                    public boolean compare(Double left, Double right) {
                        return left < right;
                    }
                },
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "GREATER_THAN";
                    }

                    @Override
                    public boolean compare(Double left, Double right) {
                        return left > right;
                    }
                }
        );
    }

    @Override
    public List<ScriptFunction<Double, ?>> getFunctions() {
        return List.of();
    }

    @Override
    public Double getValue() {
        return value;
    }
}
