package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.zps.commands.api.MappedArgumentType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
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

        // Equality check for BlockPos
        event.register(new ScriptMapper2<>(
                "==",
                BlockPos.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:boolean"),
                (blockPos, context) -> blockPos.equals(context.otherValue()),
                new MappedArgumentType<>(
                        BlockPosArgument.blockPos(),
                        Coordinates::getBlockPos,
                        Coordinates.class
                )
        ));

        // Equality check for BlockState
        event.register(new ScriptMapper2<>(
                "==",
                BlockState.class,
                Boolean.class,
                ResourceLocation.parse("zps:block_state"),
                ResourceLocation.parse("zps:boolean"),
                (blockstate, context) -> blockstate.equals(context.otherValue()),
                new MappedArgumentType<>(
                        BlockStateArgument.block(event.buildContext()),
                        ((blockInput, commandSourceStack) -> blockInput.getState()),
                        BlockInput.class
                )
        ));

        // Equality check for int
        event.register(new ScriptMapper2<>(
                "==",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value.equals(context.otherValue()),
                MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class)
        ));

        // Greater than for int
        event.register(new ScriptMapper2<>(
                ">",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value > context.otherValue(),
                MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class)
        ));

        // Less than for int
        event.register(new ScriptMapper2<>(
                "<",
                Integer.class,
                Boolean.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:boolean"),
                (value, context) -> value < context.otherValue(),
                MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class)
        ));

        // Addition for int
        event.register(new ScriptMapper2<>(
                "+",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                (value, context) -> value + context.otherValue(),
                MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class)
        ));

        // Subtraction for int
        event.register(new ScriptMapper2<>(
                "-",
                Integer.class,
                Integer.class,
                ResourceLocation.parse("zps:int"),
                ResourceLocation.parse("zps:int"),
                (value, context) -> value - context.otherValue(),
                MappedArgumentType.simple(IntegerArgumentType.integer(), Integer.class)
        ));

        // Equality check for dimension
        event.register(new ScriptMapper2<>(
                "==",
                String.class,
                Boolean.class,
                ResourceLocation.parse("zps:dimension"),
                ResourceLocation.parse("zps:boolean"),
                (dimension, context) -> dimension.equals(context.otherValue()),
                new MappedArgumentType<>(
                        DimensionArgument.dimension(),
                        (dim, source) -> dim.toString(),
                        ResourceLocation.class
                )
        ));
    }
}
