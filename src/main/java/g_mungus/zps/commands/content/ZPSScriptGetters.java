package g_mungus.zps.commands.content;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.reactor.ReactorWallBlock;
import g_mungus.zps.util.BookComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

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

        Set<String> lecternBlocks = Set.of("zps:data_lectern", "minecraft:lectern");

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
                Set.of("zps:robotic_arm")
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
                Set.of("zps:robotic_arm")
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
                Set.of("zps:radio_transmitter", "zps:radio_receiver")
        ));

        // The gauge's own readings, whichever of the two its dial is set to show: pressure in
        // Pascals and temperature in Kelvin, unscaled and unclamped by the bounds on the dial.
        Set<String> gasGaugeBlocks = Set.of("zps:gas_gauge");

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

        // A reactor read from any block of its shell. Tied to the wall tag rather than a list of
        // blocks, so wall a datapack adds is offered these too.
        Set<String> reactorWalls = Set.of("#" + ReactorWallBlock.REACTOR_WALL.location());

        event.register(ScriptGetter.withBlocks(
                "reactor_pressure",
                Double.class,
                ResourceLocation.parse("zps:double"),
                scriptContext -> reactorPressure(scriptContext.level(), scriptContext.pos()),
                reactorWalls
        ));

        event.register(ScriptGetter.withBlocks(
                "reactor_temperature",
                Double.class,
                ResourceLocation.parse("zps:double"),
                scriptContext -> reactorTemperature(scriptContext.level(), scriptContext.pos()),
                reactorWalls
        ));

        event.register(ScriptGetter.withBlocks(
                "reactor_output",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                scriptContext -> reactorOutput(scriptContext.level(), scriptContext.pos()),
                reactorWalls
        ));
    }

    /** Chamber pressure in Pascals, or zero where there is no reactor or its chamber is not simulated. */
    public static double reactorPressure(ServerLevel level, BlockPos wall) {
        ReactorManager.ChamberReading reading = chamberReading(level, wall);
        return reading == null ? 0.0 : reading.pressurePa();
    }

    /** Chamber temperature in Kelvin, or zero where there is no reactor or its chamber is not simulated. */
    public static double reactorTemperature(ServerLevel level, BlockPos wall) {
        ReactorManager.ChamberReading reading = chamberReading(level, wall);
        return reading == null ? 0.0 : reading.temperatureK();
    }

    /**
     * FE per tick the exchangers are drawing out of the chamber, averaged over the same short
     * window the reactor's displays use so it does not flicker. Zero where there is no reactor.
     */
    public static int reactorOutput(ServerLevel level, BlockPos wall) {
        Reactor reactor = ReactorManager.get(level).reactorForWall(level, wall);
        return reactor == null ? 0 : reactor.feOutAverage(level.getGameTime());
    }

    private static ReactorManager.@Nullable ChamberReading chamberReading(ServerLevel level, BlockPos wall) {
        ReactorManager manager = ReactorManager.get(level);
        Reactor reactor = manager.reactorForWall(level, wall);
        return reactor == null ? null : manager.reading(level, reactor);
    }
}
