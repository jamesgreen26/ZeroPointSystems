package g_mungus.zps.commands.content;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.util.BookComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Set;

@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class ZPSScriptGetters {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(new ScriptGetter<>(
                "pos",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                ScriptContext::pos,
                null
        ));

        event.register(new ScriptGetter<>(
                "block",
                BlockState.class,
                ResourceLocation.parse("zps:block_state"),
                scriptContext -> scriptContext.level().getBlockState(scriptContext.pos()),
                null
        ));

        event.register(new ScriptGetter<>(
                "dimension",
                String.class,
                ResourceLocation.parse("zps:dimension"),
                scriptContext -> scriptContext.level().dimension().location().toString(),
                null
        ));

        event.register(new ScriptGetter<>(
                "redstone",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                scriptContext -> scriptContext.level().getBestNeighborSignal(scriptContext.pos()),
                null
        ));

        Set<ResourceLocation> lecternBlocks = Set.of(
                ZPSMod.resource("data_lectern"),
                ResourceLocation.withDefaultNamespace("lectern")
        );

        event.register(ScriptGetter.withBlocks(
                "page_number",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                        return holder.zps$getCurrentPage() + 1; // 1-based, matching set_page
                    }
                    return 0;
                },
                lecternBlocks
        ));

        event.register(ScriptGetter.withBlocks(
                "page_contents",
                String.class,
                ResourceLocation.parse("zps:string"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                        ItemStack book = holder.zps$getBook();
                        if (book == null) return "";
                        int page = holder.zps$getCurrentPage();
                        return BookComponents.getPageText(book, page, false).replace("\n", "\\n");
                    }
                    return "";
                },
                lecternBlocks
        ));

        event.register(ScriptGetter.withBlocks(
                "held_item",
                ItemStack.class,
                ResourceLocation.parse("zps:item"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof RoboticArmBlockEntity roboticArm) {
                        return roboticArm.getHeldStack().copy();
                    }
                    return ItemStack.EMPTY;
                },
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(ScriptGetter.withBlocks(
                "transfer_count",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof RoboticArmBlockEntity roboticArm) {
                        return roboticArm.getRetrieveAmount();
                    }
                    return 0;
                },
                Set.of(ZPSMod.resource("robotic_arm"))
        ));

        event.register(ScriptGetter.withBlocks(
                "frequency",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof RadioBlockEntity radio) {
                        return radio.getRadioFrequency();
                    }
                    return 0;
                },
                Set.of(ZPSMod.resource("radio_transmitter"), ZPSMod.resource("radio_receiver"))
        ));

        // The gauge's own readings, whichever of the two its dial is set to show: pressure in
        // Pascals and temperature in Kelvin, unscaled and unclamped by the bounds on the dial.
        Set<ResourceLocation> gasGaugeBlocks = Set.of(ZPSMod.resource("gas_gauge"));

        event.register(ScriptGetter.withBlocks(
                "pressure",
                Double.class,
                ResourceLocation.parse("zps:double"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof GasGaugeBlockEntity gauge) {
                        return gauge.getPressure();
                    }
                    return 0.0;
                },
                gasGaugeBlocks
        ));

        event.register(ScriptGetter.withBlocks(
                "temperature",
                Double.class,
                ResourceLocation.parse("zps:double"),
                scriptContext -> {
                    BlockEntity be = scriptContext.level().getBlockEntity(scriptContext.pos());
                    if (be instanceof GasGaugeBlockEntity gauge) {
                        return gauge.getTemperature();
                    }
                    return 0.0;
                },
                gasGaugeBlocks
        ));
    }
}
