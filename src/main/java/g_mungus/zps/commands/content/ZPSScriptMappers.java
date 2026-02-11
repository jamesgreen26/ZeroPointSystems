package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.predicate.BlockPredicate;
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
                (vec3, context) -> vec3.distanceTo(context.argumentValue().getPosition(context.commandSource())),
                Vec3Argument.vec3(),
                Coordinates.class
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

        // Equality check for BlockPos
        event.register(new ScriptMapper2<>(
                "==",
                BlockPos.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:boolean"),
                (blockPos, context) -> blockPos.equals(context.argumentValue().getBlockPos(context.commandSource())),
                BlockPosArgument.blockPos(),
                Coordinates.class
        ));

        // Equality check for BlockState
        event.register(new ScriptMapper2<>(
                "==",
                BlockState.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_state"),
                ResourceLocation.parse("zps:boolean"),
                (blockstate, context) -> {
                    @SuppressWarnings("ConstantConditions")
                    BlockInWorld block = new BlockInWorld(null, null, false);
                    BlockInWorldMutable blockInWorldMutable = ((BlockInWorldMutable) block);
                    blockInWorldMutable.zps$setState(blockstate);
                    blockInWorldMutable.zps$setCachedEntity(true);
                    return context.argumentValue().test(block);
                },
                BlockPredicateArgument.blockPredicate(event.buildContext()),
                BlockPredicateArgument.Result.class
        ));

        // Equality check for int
        event.register(new ScriptMapper2<>(
                "==",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value.equals(context.argumentValue()),
                IntegerArgumentType.integer(),
                Integer.class
        ));

        // Greater than for int
        event.register(new ScriptMapper2<>(
                ">",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value > context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class
        ));

        // Less than for int
        event.register(new ScriptMapper2<>(
                "<",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value < context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class
        ));

        // Addition for int
        event.register(new ScriptMapper2<>(
                "+",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                (value, context) -> value + context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class
        ));

        // Subtraction for int
        event.register(new ScriptMapper2<>(
                "-",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                (value, context) -> value - context.argumentValue(),
                IntegerArgumentType.integer(),
                Integer.class
        ));

        // Equality check for double
        event.register(new ScriptMapper2<>(
                "==",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value.equals(context.argumentValue()),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Greater than for double
        event.register(new ScriptMapper2<>(
                ">",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value > context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Less than for double
        event.register(new ScriptMapper2<>(
                "<",
                Double.class,
                Boolean.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value < context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Addition for double
        event.register(new ScriptMapper2<>(
                "+",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                (value, context) -> value + context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Subtraction for double
        event.register(new ScriptMapper2<>(
                "-",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                (value, context) -> value - context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Multiplication for double
        event.register(new ScriptMapper2<>(
                "*",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                (value, context) -> value * context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Division for double
        event.register(new ScriptMapper2<>(
                "/",
                Double.class,
                Double.class,
                ResourceLocation.parse("zps:double"),
                ResourceLocation.parse("zps:double"),
                (value, context) -> value / context.argumentValue(),
                DoubleArgumentType.doubleArg(),
                Double.class
        ));

        // Equality check for dimension
        event.register(new ScriptMapper2<>(
                "==",
                String.class,
                Boolean.class,
                ResourceLocation.parse("zps:dimension"),
                ResourceLocation.parse("zps:boolean"),
                (dimension, context) -> dimension.equals(context.argumentValue().getPath()),
                DimensionArgument.dimension(),
                ResourceLocation.class
        ));
    }

    public interface BlockInWorldMutable {
        void zps$setState(BlockState state);
        void zps$setCachedEntity(boolean b);
    }
}
