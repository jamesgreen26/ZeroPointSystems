package g_mungus.zps.commands.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.reactor.ReactorWallBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.List;
import java.util.Map;

/**
 * {@code /zps_debug reactor [pos]} — everything about the reactor at a position, or the one the
 * player is looking at.
 */
public class ReactorDebugCommand {
    private static final String ARG_POS = "pos";
    private static final double REACH = 20.0;

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("reactor")
                            .executes(context -> dumpLookedAt(context.getSource()))
                            .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                    .executes(context -> dump(context.getSource(),
                                            BlockPosArgument.getLoadedBlockPos(context, ARG_POS)))));

    private static int dumpLookedAt(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(REACH, 0, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a reactor block, or give a position"));
            return 0;
        }
        return dump(source, blockHit.getBlockPos());
    }

    private static int dump(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        ReactorManager manager = ReactorManager.get(level);
        List<Reactor> reactors = manager.reactorsAt(pos);

        if (reactors.isEmpty()) {
            BlockState state = level.getBlockState(pos);
            String what = state.is(ReactorWallBlock.REACTOR_WALL)
                    ? "a reactor wall block that is not part of any sealed cavity"
                    : "not a reactor block";
            source.sendSuccess(() -> Component.literal(pos.toShortString() + ": " + what), false);
            return 0;
        }

        for (Reactor reactor : reactors) {
            for (String line : describe(level, reactor, pos)) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return reactors.size();
    }

    private static List<String> describe(ServerLevel level, Reactor reactor, BlockPos pos) {
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos host = reactor.hostNodePos(level);
        boolean nodePresent = kelvin.getNodeAt(host) instanceof ReactorChamberNode;

        List<String> lines = new java.util.ArrayList<>();
        lines.add(String.format("Reactor #%d — %s is %s", reactor.id(), pos.toShortString(),
                reactor.isInterior(pos) ? "interior" : "wall"));
        lines.add(String.format("  host %s, chunk %s, node %s",
                reactor.host().toShortString(),
                level.isLoaded(reactor.host()) ? "loaded" : "unloaded",
                nodePresent ? "present" : "missing"));
        lines.add(String.format("  volume %d m^3, walls %d, compactness %.3f",
                reactor.volume(), reactor.wallCount(), reactor.compactness()));
        lines.add(String.format("  burst %.2f MPa, wall heat capacity %.0f J/K",
                reactor.burstPressure() / 1e6, reactor.wallHeatCapacity()));

        if (nodePresent) {
            double temperature = kelvin.getTemperatureAt(host);
            double pressure = kelvin.getPressureAt(host);
            Map<GasType, Double> masses = kelvin.getGasMassAt(host);
            lines.add(String.format("  %.0f K (ignition %.0f, melt %.0f), %.3f MPa (%.0f%% of burst)",
                    temperature, ZPSConfig.reactorIgnitionTemperatureK(), ZPSConfig.reactorMeltTemperatureK(),
                    pressure / 1e6, 100.0 * pressure / reactor.burstPressure()));
            lines.add(String.format("  energy %.3e J, heat capacity %.0f J/K",
                    kelvin.getHeatEnergy(host), kelvin.getNodeHeatCapacity(host)));
            if (masses.isEmpty()) {
                lines.add("  gas: none");
            } else {
                StringBuilder gas = new StringBuilder("  gas:");
                for (Map.Entry<GasType, Double> entry : masses.entrySet()) {
                    gas.append(String.format(" %s %.4f kg", entry.getKey().getName(), entry.getValue()));
                }
                gas.append(String.format(" (aether %.0f%%)", 100.0 * ReactorManager.aetherFraction(masses)));
                lines.add(gas.toString());
            }
        }

        lines.add(String.format("  %s, ignited before: %s",
                reactor.isLit() ? "lit" : "cold",
                reactor.hasIgnited()));
        lines.add(String.format("  FE last tick: in %d, out %d", reactor.feInLastTick(), reactor.feOutLastTick()));

        lines.add("  " + count(level, reactor, ModBlocks.FUEL_INJECTOR.get(), "injectors")
                + ", " + count(level, reactor, ModBlocks.EXHAUST_PORT.get(), "exhaust ports")
                + ", " + count(level, reactor, ModBlocks.HEAT_EXCHANGER.get(), "exchangers"));
        return lines;
    }

    private static String count(ServerLevel level, Reactor reactor, Block block, String name) {
        List<BlockPos> all = reactor.wallsOf(level, block);
        int oriented = 0;
        StringBuilder misoriented = new StringBuilder();
        for (BlockPos pos : all) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.FACING)
                    && reactor.isOriented(pos, state.getValue(BlockStateProperties.FACING))) {
                oriented++;
            } else {
                misoriented.append(' ').append(pos.toShortString());
            }
        }
        String summary = oriented + " " + name;
        if (oriented < all.size()) {
            summary += " (" + (all.size() - oriented) + " misoriented:" + misoriented + ")";
        }
        return summary;
    }
}
