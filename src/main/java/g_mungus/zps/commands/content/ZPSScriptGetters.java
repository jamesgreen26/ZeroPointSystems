package g_mungus.zps.commands.content;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.CreativePowerCellBlockEntity;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptContext;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.reactor.ReactorWallBlock;
import g_mungus.zps.util.BookComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;


@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class ZPSScriptGetters {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(ZPSNodes.getter("pos", ZPSScriptTypes.BLOCK_POS, ZPSScriptContext::pos));

        event.register(ZPSNodes.getter("block", ZPSScriptTypes.BLOCK_STATE,
                context -> context.level().getBlockState(context.pos())));

        event.register(ZPSNodes.getter("dimension", ZPSScriptTypes.DIMENSION,
                context -> context.level().dimension().location().toString()));

        event.register(ZPSNodes.getter("redstone", BuiltInTypes.INT,
                context -> context.level().getBestNeighborSignal(context.pos())));

        BlockApplicability lecterns = BlockApplicability.of("zps:data_lectern", "minecraft:lectern");

        event.register(ZPSNodes.getter("page_number", BuiltInTypes.INT, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                return holder.zps$getCurrentPage() + 1; // 1-based, matching set_page
            }
            return 0;
        }).withApplicability(lecterns));

        event.register(ZPSNodes.getter("page_contents", BuiltInTypes.STRING, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                ItemStack book = holder.zps$getBook();
                if (book == null) return "";
                int page = holder.zps$getCurrentPage();
                return BookComponents.getPageText(book, page, false).replace("\n", "\\n");
            }
            return "";
        }).withApplicability(lecterns));

        BlockApplicability roboticArm = BlockApplicability.of("zps:robotic_arm");

        event.register(ZPSNodes.getter("held_item", ZPSScriptTypes.ITEM, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof RoboticArmBlockEntity arm) {
                return arm.getHeldStack().copy();
            }
            return ItemStack.EMPTY;
        }).withApplicability(roboticArm));

        event.register(ZPSNodes.getter("transfer_count", BuiltInTypes.INT, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof RoboticArmBlockEntity arm) {
                return arm.getRetrieveAmount();
            }
            return 0;
        }).withApplicability(roboticArm));

        event.register(ZPSNodes.getter("frequency", BuiltInTypes.INT, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof RadioBlockEntity radio) {
                return radio.getRadioFrequency();
            }
            return 0;
        }).withApplicability(BlockApplicability.of("zps:radio_transmitter", "zps:radio_receiver")));

        // A power cell structure pools its energy, so any of its cells reads the whole battery.
        event.register(ZPSNodes.getter("stored_energy", BuiltInTypes.INT,
                context -> storedEnergy(context.level(), context.pos()))
                .withApplicability(BlockApplicability.of("zps:power_cell", "zps:creative_power_cell")));

        // Whatever the gauge's dial is set to show: pressure in Pascals or temperature in Kelvin,
        // unscaled and unclamped by the bounds on the dial. Cycling the mode changes what this reads.
        event.register(ZPSNodes.getter("gauge_value", BuiltInTypes.DOUBLE, context -> {
            BlockEntity be = context.level().getBlockEntity(context.pos());
            if (be instanceof GasGaugeBlockEntity gauge) {
                return gauge.getMeasuredValue();
            }
            return 0.0;
        }).withApplicability(BlockApplicability.of("zps:gas_gauge")));

        // A reactor read from any block of its shell. Tied to the wall tag rather than a list of
        // blocks, so wall a datapack adds is offered these too.
        BlockApplicability reactorWalls = BlockApplicability.of("#" + ReactorWallBlock.REACTOR_WALL.location());

        event.register(ZPSNodes.getter("reactor_pressure", BuiltInTypes.DOUBLE,
                context -> reactorPressure(context.level(), context.pos())).withApplicability(reactorWalls));

        event.register(ZPSNodes.getter("reactor_temperature", BuiltInTypes.DOUBLE,
                context -> reactorTemperature(context.level(), context.pos())).withApplicability(reactorWalls));

        event.register(ZPSNodes.getter("reactor_output", BuiltInTypes.INT,
                context -> reactorOutput(context.level(), context.pos())).withApplicability(reactorWalls));
    }

    /**
     * FE held by the power cell structure this block belongs to, read from any of its cells. A
     * creative cell reads as {@link Integer#MAX_VALUE}, the same figure its energy capability
     * reports, so a comparison against it behaves as "always full". Zero where there is no cell.
     */
    public static int storedEnergy(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PowerCellBlockEntity cell) {
            return cell.getEnergyStored();
        }
        if (be instanceof CreativePowerCellBlockEntity) {
            return Integer.MAX_VALUE;
        }
        return 0;
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
        Reactor reactor = reactorAt(level, wall);
        return reactor == null ? 0 : reactor.feOutAverage(level.getGameTime());
    }

    private static ReactorManager.@Nullable ChamberReading chamberReading(ServerLevel level, BlockPos wall) {
        Reactor reactor = reactorAt(level, wall);
        return reactor == null ? null : ReactorManager.get(level).reading(level, reactor);
    }

    /**
     * The reactor a wall block belongs to. Edge and corner blocks have no face on the cavity, so
     * they are part of no reactor themselves; for those, the reactor of a touching wall block is
     * used instead, face neighbours first so an edge prefers the wall it shares a face with.
     */
    private static @Nullable Reactor reactorAt(ServerLevel level, BlockPos wall) {
        ReactorManager manager = ReactorManager.get(level);
        Reactor reactor = manager.reactorForWall(level, wall);
        if (reactor != null || !level.getBlockState(wall).is(ReactorWallBlock.REACTOR_WALL)) {
            return reactor;
        }
        for (Direction direction : Direction.values()) {
            reactor = manager.reactorForWall(level, wall.relative(direction));
            if (reactor != null) {
                return reactor;
            }
        }
        for (BlockPos neighbour : BlockPos.betweenClosed(wall.offset(-1, -1, -1), wall.offset(1, 1, 1))) {
            reactor = manager.reactorForWall(level, neighbour);
            if (reactor != null) {
                return reactor;
            }
        }
        return null;
    }
}
