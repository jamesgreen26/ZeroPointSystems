package g_mungus.zps.commands.lang.v2.classes;

import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;

import java.util.List;

public record BlockPosClass(String name) implements ScriptClass<BlockPos> {

    @Override
    public Class<BlockPos> getType() {
        return BlockPos.class;
    }

    @Override
    public MappedArgumentType<?, BlockPos> getArgumentType() {
        return new MappedArgumentType<>(BlockPosArgument.blockPos(), Coordinates::getBlockPos, Coordinates.class);
    }

    @Override
    public List<ScriptComparator<BlockPos>> getComparators() {
        return List.of(
                new ScriptComparator<>() {
                    @Override
                    public String getName() {
                        return "EQUALS";
                    }

                    @Override
                    public boolean compare(BlockPos left, BlockPos right) {
                        return left.equals(right);
                    }
                }
        );
    }

    @Override
    public List<ScriptFunction<BlockPos, ?>> getFunctions() {
        return List.of(
                ScriptFunction.simple("X", blockPosScriptClass ->
                        ScriptObject.withDefaultType("X", blockPosScriptClass.value().getX())),
                ScriptFunction.simple("Y", blockPosScriptClass ->
                        ScriptObject.withDefaultType("Y", blockPosScriptClass.value().getY())),
                ScriptFunction.simple("Z", blockPosScriptClass ->
                        ScriptObject.withDefaultType("Z", blockPosScriptClass.value().getY())),
                new ScriptFunction<>("DISTANCE", (pos, context) ->
                        ScriptObject.withDefaultType("DISTANCE", pos.value().getCenter().distanceTo(context.getVecPos())))
        );
    }
}
