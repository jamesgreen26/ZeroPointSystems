package g_mungus.zps.commands.lang.v2.classes;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;

import java.util.List;

public record IntegerClass(String name) implements ScriptClass<Integer> {

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public MappedArgumentType<?, Integer> getArgumentType() {
        return MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class);
    }

    @Override
    public List<ScriptComparator<Integer>> getComparators() {
        return List.of(
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "EQUALS";
                    }

                    @Override
                    public boolean compare(Integer left, Integer right) {
                        return left.equals(right);
                    }
                },
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "LESS_THAN";
                    }

                    @Override
                    public boolean compare(Integer left, Integer right) {
                        return left < right;
                    }
                },
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "GREATER_THAN";
                    }

                    @Override
                    public boolean compare(Integer left, Integer right) {
                        return left > right;
                    }
                }
        );
    }

    @Override
    public List<ScriptFunction<Integer, ?>> getFunctions() {
        return List.of();
    }
}
