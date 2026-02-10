package g_mungus.zps.commands.lang.v2.classes;

import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.world.phys.Vec3;


import java.util.List;

public record Vec3Class(String name, List<ScriptFunction<Vec3, ?>> functions) implements ScriptClass<Vec3> {

    @Override
    public Class<Vec3> getType() {
        return Vec3.class;
    }

    @Override
    public MappedArgumentType<?, Vec3> getArgumentType() {
        return new MappedArgumentType<>(Vec3Argument.vec3(), Coordinates::getPosition, Coordinates.class);
    }

    @Override
    public List<ScriptComparator<Vec3>> getComparators() {
        return List.of(
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "EQUALS";
                    }

                    @Override
                    public boolean compare(Vec3 left, Vec3 right) {
                        return left.equals(right);
                    }
                }
        );
    }

    @Override
    public List<ScriptFunction<Vec3, ?>> getFunctions() {
        return functions;
    }

    public static class Functions {
        public static ScriptFunction<Vec3, ?> X = ScriptFunction.simple("X", vec3Class ->
                ScriptObject.withDefaultType("X", vec3Class.value().x()));
        public static ScriptFunction<Vec3, ?> Y = ScriptFunction.simple("Y", vec3Class ->
                ScriptObject.withDefaultType("Y", vec3Class.value().y()));
        public static ScriptFunction<Vec3, ?> Z = ScriptFunction.simple("Z", vec3Class ->
                ScriptObject.withDefaultType("Z", vec3Class.value().z()));
        public static ScriptFunction<Vec3, ?> VOLUME = ScriptFunction.simple("VOLUME", vec3Class ->
                ScriptObject.withDefaultType("VOLUME", vec3Class.value().x() * vec3Class.value().y() * vec3Class.value().z()));
        public static ScriptFunction<Vec3, ?> DISTANCE = new ScriptFunction<>("DISTANCE", (vec3Class, context) ->
                ScriptObject.withDefaultType("DISTANCE", vec3Class.value().distanceTo(context.getVecPos())));
        public static ScriptFunction<Vec3, ?> LENGTH = ScriptFunction.simple("LENGTH", vec3Class ->
                ScriptObject.withDefaultType("LENGTH", vec3Class.value().distanceTo(new Vec3(0, 0, 0))));
    }
}
