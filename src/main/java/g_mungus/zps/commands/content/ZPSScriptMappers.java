package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.commands.content.executors.DimensionIndexCommand;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber
public class ZPSScriptMappers {
    private static final String ESCAPED_NEWLINE = "\\n";
    private static final Pattern ESCAPED_NEWLINE_PATTERN = Pattern.compile(Pattern.quote(ESCAPED_NEWLINE));

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatVec3(Vec3 vec3) {
        return formatDouble(vec3.x) + " " + formatDouble(vec3.y) + " " + formatDouble(vec3.z);
    }

    private static Vec3 resolveVec3Argument(Object argumentValue, net.minecraft.commands.CommandSourceStack commandSource) {
        if (argumentValue instanceof Coordinates coordinates) {
            return coordinates.getPosition(commandSource);
        }
        if (argumentValue instanceof Vec3 vec3) {
            return vec3;
        }
        throw new IllegalArgumentException("Expected coordinates or Vec3 argument, got: " + argumentValue);
    }

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(new ScriptMapper<>(
                "x",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getX()
        ));

        event.register(new ScriptMapper<>(
                "y",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getY()
        ));

        event.register(new ScriptMapper<>(
                "z",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getZ()
        ));

        event.register(new ScriptMapper<>(
                "center",
                BlockPos.class,
                Vec3.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:vec_pos"),
                (blockPos, scriptContext) -> blockPos.getCenter()
        ));

        // Vec3 Position - X coordinate
        event.register(new ScriptMapper<>(
                "x",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.x
        ));

        // Vec3 Position - Y coordinate
        event.register(new ScriptMapper<>(
                "y",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.y
        ));

        // Vec3 Position - Z coordinate
        event.register(new ScriptMapper<>(
                "z",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.z
        ));

        // Vec3 Position - Distance to another position
        event.register(new ScriptMapper2<>(
                "distance_to",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:double"),
                "coordinates",
                (vec3, context) -> vec3.distanceTo(resolveVec3Argument(context.argumentValue(), context.commandSource())),
                Vec3Argument.vec3(),
                Coordinates.class,
                ResourceLocation.parse("zps:vec_pos")
        ));

        // Vec3 Position - Direction to another position
        event.register(new ScriptMapper2<>(
                "direction_to",
                Vec3.class,
                Vec3.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:vec_dir"),
                "coordinates",
                (vec3, context) -> resolveVec3Argument(context.argumentValue(), context.commandSource()).subtract(vec3).normalize(),
                Vec3Argument.vec3(),
                Coordinates.class,
                ResourceLocation.parse("zps:vec_pos")
        ));

        // Vec3 Box - X dimension
        event.register(new ScriptMapper<>(
                "x",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_box"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.x
        ));

        // Vec3 Box - Y dimension
        event.register(new ScriptMapper<>(
                "y",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_box"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.y
        ));

        // Vec3 Box - Z dimension
        event.register(new ScriptMapper<>(
                "z",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_box"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.z
        ));

        // Vec3 Box - Volume
        event.register(new ScriptMapper<>(
                "volume",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_box"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.x * vec3.y * vec3.z
        ));

        // Vec3 Direction - X component
        event.register(new ScriptMapper<>(
                "x",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.x
        ));

        // Vec3 Direction - Y component
        event.register(new ScriptMapper<>(
                "y",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.y
        ));

        // Vec3 Direction - Z component
        event.register(new ScriptMapper<>(
                "z",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.z
        ));

        // Vec3 Direction - Length (magnitude)
        event.register(new ScriptMapper<>(
                "length",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:double"),
                (vec3, scriptContext) -> vec3.length()
        ));

        // Vec3 Direction - Normalize
        event.register(new ScriptMapper<>(
                "normalize",
                Vec3.class,
                Vec3.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:vec_dir"),
                (vec3, scriptContext) -> vec3.normalize()
        ));

        // Vec3 Direction - Cross product with another direction
        event.register(new ScriptMapper2<>(
                "cross",
                Vec3.class,
                Vec3.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:vec_dir"),
                "direction",
                (vec3, context) -> vec3.cross(resolveVec3Argument(context.argumentValue(), context.commandSource())),
                Vec3Argument.vec3(),
                Coordinates.class,
                ResourceLocation.parse("zps:vec_dir")
        ));

        // Vec3 Direction - Dot product with another direction
        event.register(new ScriptMapper2<>(
                "dot",
                Vec3.class,
                Double.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:double"),
                "direction",
                (vec3, context) -> vec3.dot(resolveVec3Argument(context.argumentValue(), context.commandSource())),
                Vec3Argument.vec3(),
                Coordinates.class,
                ResourceLocation.parse("zps:vec_dir")
        ));

        // Equality check for BlockPos
        event.register(new ScriptMapper2<>(
                "==",
                BlockPos.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:boolean"),
                "coordinates",
                (blockPos, context) -> blockPos.equals(context.argumentValue().getBlockPos(context.commandSource())),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                ResourceLocation.parse("zps:block_pos")
        ));

        // Vec3 Position - Rounded down to BlockPos
        event.register(new ScriptMapper<>(
                "rounded_down",
                Vec3.class,
                BlockPos.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:block_pos"),
                (vec3, scriptContext) -> BlockPos.containing(vec3)
        ));

        // Equality check for BlockState
        event.register(new ScriptMapper2<>(
                "==",
                BlockState.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_state"),
                ResourceLocation.parse("zps:boolean"),
                "block",
                (blockstate, context) -> {
                    @SuppressWarnings("ConstantConditions")
                    BlockInWorld block = new BlockInWorld(null, new BlockPos(0,0,0), false);
                    BlockInWorldMutable blockInWorldMutable = ((BlockInWorldMutable) block);
                    blockInWorldMutable.zps$setState(blockstate);
                    blockInWorldMutable.zps$setCachedEntity(true);
                    return context.argumentValue().test(block);
                },
                BlockPredicateArgument.blockPredicate(event.buildContext()),
                BlockPredicateArgument.Result.class,
                null
        ));

        // Equality check for int
        event.register(new ScriptMapper2<>(
                "==",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                "int",
                (value, context) -> value.equals(context.argumentValue()),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Greater than for int
        event.register(new ScriptMapper2<>(
                ">",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                "int",
                (value, context) -> value > context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Less than for int
        event.register(new ScriptMapper2<>(
                "<",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                "int",
                (value, context) -> value < context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Addition for int
        event.register(new ScriptMapper2<>(
                "+",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value + context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Subtraction for int
        event.register(new ScriptMapper2<>(
                "-",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value - context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Bitwise AND for int
        event.register(new ScriptMapper2<>(
                "&",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value & context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Bitwise OR for int
        event.register(new ScriptMapper2<>(
                "|",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value | context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Left shift for int
        event.register(new ScriptMapper2<>(
                "<<",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value << context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Right shift for int
        event.register(new ScriptMapper2<>(
                ">>",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value >> context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Modulo for int
        event.register(new ScriptMapper2<>(
                "%",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                "int",
                (value, context) -> value % context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Multiplication for int -> double
        event.register(new ScriptMapper2<>(
                "*",
                Integer.class,
                Double.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value * context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Division for int -> double
        event.register(new ScriptMapper2<>(
                "/",
                Integer.class,
                Double.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value / context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Equality check for double
        event.register(new ScriptMapper2<>(
                "==",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                "double",
                (value, context) -> value.equals(context.argumentValue()),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Greater than for double
        event.register(new ScriptMapper2<>(
                ">",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                "double",
                (value, context) -> value > context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Less than for double
        event.register(new ScriptMapper2<>(
                "<",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                "double",
                (value, context) -> value < context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Addition for double
        event.register(new ScriptMapper2<>(
                "+",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value + context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Subtraction for double
        event.register(new ScriptMapper2<>(
                "-",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value - context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Multiplication for double
        event.register(new ScriptMapper2<>(
                "*",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value * context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Division for double
        event.register(new ScriptMapper2<>(
                "/",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                "double",
                (value, context) -> value / context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class,
                ResourceLocation.parse("zps:double")
        ));

        // Round down double to int
        event.register(new ScriptMapper<>(
                "rounded_down",
                Double.class,
                Integer.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:int"),
                (value, scriptContext) -> (int) Math.floor(value)
        ));

        // Round up double to int
        event.register(new ScriptMapper<>(
                "rounded_up",
                Double.class,
                Integer.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:int"),
                (value, scriptContext) -> (int) Math.ceil(value)
        ));

        // Equality check for string
        event.register(new ScriptMapper2<>(
                "==",
                String.class,
                Boolean.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:boolean"),
                "string",
                (str, context) -> str.equals(context.argumentValue()),
                StringArgumentType.string(),
                String.class,
                ResourceLocation.parse("zps:string")
        ));

        // Concatenation for string
        event.register(new ScriptMapper2<>(
                "+",
                String.class,
                String.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:string"),
                "string",
                (str, context) -> str + context.argumentValue(),
                StringArgumentType.string(),
                String.class,
                ResourceLocation.parse("zps:string")
        ));

        // Prepend for string
        event.register(new ScriptMapper2<>(
                "<+",
                String.class,
                String.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:string"),
                "string",
                (str, context) -> context.argumentValue() + str,
                StringArgumentType.string(),
                String.class,
                ResourceLocation.parse("zps:string")
        ));

        // Count escaped "\n"-delimited lines in a string
        event.register(new ScriptMapper<>(
                "lines",
                String.class,
                Integer.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:int"),
                (str, ctx) -> splitEscapedNewlines(str).length
        ));

        // Get the escaped "\n"-delimited line at the given index
        event.register(new ScriptMapper2<>(
                "get_line",
                String.class,
                String.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:string"),
                "index",
                (str, context) -> {
                    String[] lines = splitEscapedNewlines(str);
                    int index = context.argumentValue() - 1; // 1-based indexing
                    return index >= 0 && index < lines.length ? lines[index] : "";
                },
                IntegerArgumentType.integer(),
                Integer.class,
                ResourceLocation.parse("zps:int")
        ));

        // Equality check for dimension
        event.register(new ScriptMapper2<>(
                "==",
                String.class,
                Boolean.class,
                ResourceLocation.parse("zps:dimension"),
                ResourceLocation.parse("zps:boolean"),
                "dimension",
                (dimension, context) -> dimension.equals(context.argumentValue().toString()),
                DimensionArgument.dimension(),
                ResourceLocation.class,
                null
        ));

        // Stable integer index for each dimension
        event.register(new ScriptMapper<>(
                "index",
                String.class,
                Integer.class,
                ResourceLocation.parse("zps:dimension"),
                ResourceLocation.parse("zps:int"),
                (dim, ctx) -> DimensionIndexCommand.getIndex(
                        ctx.level().getServer(),
                        ResourceLocation.parse(dim)
                )
        ));

        // as_string for int
        event.register(new ScriptMapper<>(
                "as_string",
                Integer.class,
                String.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:string"),
                (value, ctx) -> value.toString()
        ));

        // as_string for double
        event.register(new ScriptMapper<>(
                "as_string",
                Double.class,
                String.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:string"),
                (value, ctx) -> formatDouble(value)
        ));

        // as_string for block_pos ("x y z")
        event.register(new ScriptMapper<>(
                "as_string",
                BlockPos.class,
                String.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:string"),
                (pos, ctx) -> pos.getX() + " " + pos.getY() + " " + pos.getZ()
        ));

        // as_string for vec_pos ("x y z" with 2 decimal places)
        event.register(new ScriptMapper<>(
                "as_string",
                Vec3.class,
                String.class,
                ResourceLocation.parse("zps:vec_pos"),
                ResourceLocation.parse("zps:string"),
                (vec3, ctx) -> formatVec3(vec3)
        ));

        // as_string for vec_box ("x y z" with 2 decimal places)
        event.register(new ScriptMapper<>(
                "as_string",
                Vec3.class,
                String.class,
                ResourceLocation.parse("zps:vec_box"),
                ResourceLocation.parse("zps:string"),
                (vec3, ctx) -> formatVec3(vec3)
        ));

        // as_string for vec_dir ("x y z" with 2 decimal places)
        event.register(new ScriptMapper<>(
                "as_string",
                Vec3.class,
                String.class,
                ResourceLocation.parse("zps:vec_dir"),
                ResourceLocation.parse("zps:string"),
                (vec3, ctx) -> formatVec3(vec3)
        ));

        // as_string for dimension (identity - dimension is already a string key)
        event.register(new ScriptMapper<>(
                "as_string",
                String.class,
                String.class,
                ResourceLocation.parse("zps:dimension"),
                ResourceLocation.parse("zps:string"),
                (dim, ctx) -> dim
        ));

        // string as_int
        event.register(new ScriptMapper<>(
                "as_int",
                String.class,
                Integer.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:int"),
                (value, ctx) -> Integer.parseInt(value)
        ));

        // string as_double
        event.register(new ScriptMapper<>(
                "as_double",
                String.class,
                Double.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:double"),
                (value, ctx) -> Double.parseDouble(value)
        ));

        // string as_block_pos (expects "x y z" format)
        event.register(new ScriptMapper<>(
                "as_block_pos",
                String.class,
                BlockPos.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:block_pos"),
                (value, ctx) -> {
                    String[] parts = value.split(" ");
                    return new BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    );
                }
        ));

        // string as_dimension (identity - treats the string as a dimension key)
        event.register(new ScriptMapper<>(
                "as_dimension",
                String.class,
                String.class,
                ResourceLocation.parse("zps:string"),
                ResourceLocation.parse("zps:dimension"),
                (value, ctx) -> value
        ));
    }

    public interface BlockInWorldMutable {
        void zps$setState(BlockState state);
        void zps$setCachedEntity(boolean b);
    }

    private static String[] splitEscapedNewlines(String value) {
        return ESCAPED_NEWLINE_PATTERN.split(value, -1);
    }
}
