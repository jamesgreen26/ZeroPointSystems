package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.content.arguments.AssemblerRecipeArgument;
import g_mungus.zps.commands.content.executors.AssemblerRecipeCommand;
import g_mungus.zps.commands.content.executors.RoboticArmItemCommand;
import g_mungus.zps.commands.content.executors.SetFrequencyCommand;
import g_mungus.zps.commands.content.executors.SetPageCommand;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class ZPSScriptExecutors {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(ZPSNodes.executor("set_redstone", BuiltInTypes.INT, IntegerArgumentType.integer(0, 15),
                (power, context) -> {
                    SetRedstoneCommand.setRedstone(context.level(), context.pos(), power);
                    return 1;
                }));

        BlockApplicability lecterns = BlockApplicability.of("zps:data_lectern", "minecraft:lectern");

        event.register(ZPSNodes.executor("set_page", BuiltInTypes.INT, IntegerArgumentType.integer(1, 100),
                (page, context) -> SetPageCommand.setPage(context.level(), context.pos(), page))
                .withApplicability(lecterns));

        event.register(ZPSNodes.executor("write_page", BuiltInTypes.STRING,
                (text, context) -> SetPageCommand.writeToCurrentPage(context.level(), context.pos(), text))
                .withApplicability(lecterns));

        event.register(ZPSNodes.executor("set_frequency", BuiltInTypes.INT,
                IntegerArgumentType.integer(RadioBlockEntity.MIN_FREQUENCY, RadioBlockEntity.MAX_FREQUENCY),
                (frequency, context) -> SetFrequencyCommand.setFrequency(context.level(), context.pos(), frequency))
                .withApplicability(BlockApplicability.of("zps:radio_transmitter", "zps:radio_receiver")));

        BlockApplicability roboticArm = BlockApplicability.of("zps:robotic_arm");

        event.register(ZPSNodes.executor("take_items", ZPSScriptTypes.BLOCK_POS,
                (target, context) -> RoboticArmItemCommand.takeItems(context.level(), context.pos(), target))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("put_items", ZPSScriptTypes.BLOCK_POS,
                (target, context) -> RoboticArmItemCommand.putItems(context.level(), context.pos(), target))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("use", ZPSScriptTypes.BLOCK_POS,
                (target, context) -> RoboticArmItemCommand.useItem(context.level(), context.pos(), target))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("shift_use", ZPSScriptTypes.BLOCK_POS,
                (target, context) -> RoboticArmItemCommand.shiftUseItem(context.level(), context.pos(), target))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("drop_items", ZPSScriptTypes.BLOCK_POS,
                (target, context) -> RoboticArmItemCommand.dropItems(context.level(), context.pos(), target))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("set_transfer_count", BuiltInTypes.INT,
                IntegerArgumentType.integer(RoboticArmBlockEntity.MIN_RETRIEVE_AMOUNT, RoboticArmBlockEntity.MAX_RETRIEVE_AMOUNT),
                (count, context) -> RoboticArmItemCommand.setTransferCount(context.level(), context.pos(), count))
                .withApplicability(roboticArm));

        event.register(ZPSNodes.executor("set_recipe", BuiltInTypes.STRING,
                AssemblerRecipeArgument.recipe(), ResourceLocation.class, (id, context) -> id.toString(),
                (recipeId, context) -> AssemblerRecipeCommand.setRecipe(context.level(), context.pos(), recipeId))
                .withApplicability(BlockApplicability.of("zps:assembler")));
    }
}
