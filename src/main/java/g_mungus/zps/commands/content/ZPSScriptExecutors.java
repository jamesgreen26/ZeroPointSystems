package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.content.executors.RoboticArmItemCommand;
import g_mungus.zps.commands.content.executors.AssemblerRecipeCommand;
import g_mungus.zps.commands.content.arguments.AssemblerRecipeArgument;
import g_mungus.zps.commands.content.arguments.RadioFrequencyArgument;
import g_mungus.zps.commands.content.executors.SetFrequencyCommand;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import g_mungus.zps.commands.content.executors.SetPageCommand;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber
public class ZPSScriptExecutors {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(ScriptExecutor.simple(
                "set_redstone",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                IntegerArgumentType.integer(0, 15),
                (power, context) -> {
                    SetRedstoneCommand.setRedstone(
                            context.commandSource().getLevel(),
                            context.pos(),
                            power
                    );
                    return 1;
                }
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "set_page",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                IntegerArgumentType.integer(1, 100),
                (page, context) -> SetPageCommand.setPage(
                        context.commandSource().getLevel(),
                        context.pos(),
                        page
                ),
                Set.of(ZPSMod.resource("data_lectern"), ResourceLocation.withDefaultNamespace("lectern"))
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "write_page",
                String.class,
                ResourceLocation.parse("zps:string"),
                StringArgumentType.string(),
                (text, context) -> SetPageCommand.writeToCurrentPage(
                        context.commandSource().getLevel(),
                        context.pos(),
                        text
                ),
                Set.of(ZPSMod.resource("data_lectern"), ResourceLocation.withDefaultNamespace("lectern"))
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "set_frequency",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                RadioFrequencyArgument.radioFrequency(),
                (frequencyIndex, context) -> SetFrequencyCommand.setFrequency(
                        context.commandSource().getLevel(),
                        context.pos(),
                        frequencyIndex
                ),
                Set.of(ZPSMod.resource("radio_transmitter"), ZPSMod.resource("radio_receiver"))
        ));

        event.register(new ScriptExecutor<>(
                "take_items",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                (coordinates, context) -> coordinates.getBlockPos(context.commandSource()),
                (targetPos, context) -> RoboticArmItemCommand.takeItems(
                        context.commandSource().getLevel(),
                        context.pos(),
                        targetPos
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(new ScriptExecutor<>(
                "put_items",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                (coordinates, context) -> coordinates.getBlockPos(context.commandSource()),
                (targetPos, context) -> RoboticArmItemCommand.putItems(
                        context.commandSource().getLevel(),
                        context.pos(),
                        targetPos
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(new ScriptExecutor<>(
                "use",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                (coordinates, context) -> coordinates.getBlockPos(context.commandSource()),
                (targetPos, context) -> RoboticArmItemCommand.useItem(
                        context.commandSource().getLevel(),
                        context.pos(),
                        targetPos
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(new ScriptExecutor<>(
                "shift_use",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                (coordinates, context) -> coordinates.getBlockPos(context.commandSource()),
                (targetPos, context) -> RoboticArmItemCommand.shiftUseItem(
                        context.commandSource().getLevel(),
                        context.pos(),
                        targetPos
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(new ScriptExecutor<>(
                "drop_items",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                BlockPosArgument.blockPos(),
                Coordinates.class,
                (coordinates, context) -> coordinates.getBlockPos(context.commandSource()),
                (targetPos, context) -> RoboticArmItemCommand.dropItems(
                        context.commandSource().getLevel(),
                        context.pos(),
                        targetPos
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(new ScriptExecutor<>(
                "set_recipe",
                String.class,
                ResourceLocation.parse("zps:string"),
                AssemblerRecipeArgument.recipe(),
                ResourceLocation.class,
                (id, context) -> id.toString(),
                (recipeId, context) -> AssemblerRecipeCommand.setRecipe(
                        context.level(),
                        context.pos(),
                        recipeId
                ),
                Set.of(ZPSMod.resource("assembler"))
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "set_transfer_count",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                IntegerArgumentType.integer(RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT, RoboticArmBlockEntity.MAX_RETRIEVE_AMOUNT),
                (transferCount, context) -> RoboticArmItemCommand.setTransferCount(
                        context.commandSource().getLevel(),
                        context.pos(),
                        transferCount
                ),
                Set.of(ZPSMod.resource("robotic_arm"))
        ));
    }
}
