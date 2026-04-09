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

@Mod.EventBusSubscriber
public class ZPSScriptMappers {

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
                (vec3, context) -> vec3.distanceTo(context.argumentValue().getPosition(context.commandSource())),
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
                (vec3, context) -> context.argumentValue().getPosition(context.commandSource()).subtract(vec3).normalize(),
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
                (vec3, context) -> vec3.cross(context.argumentValue().getPosition(context.commandSource())),
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
                (vec3, context) -> vec3.dot(context.argumentValue().getPosition(context.commandSource())),
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
    }

    public interface BlockInWorldMutable {
        void zps$setState(BlockState state);
        void zps$setCachedEntity(boolean b);
    }
}
