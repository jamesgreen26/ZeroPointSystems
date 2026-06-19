package g_mungus.zps.commands.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlaceBlockPanoramaCommand {
    private static final int START_RADIUS = 5;

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("place_block_panorama")
                            .executes(context -> placePanorama(context.getSource())));

    private static int placePanorama(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();

        List<Block> blocks = ModBlocks.BLOCKS.getEntries().stream()
                .map(holder -> (Block) holder.get())
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).getPath()))
                .toList();

        List<ArcPoint> points = quarterArcPoints(START_RADIUS);
        int placed = 0;
        int tier = 0;
        while (placed < blocks.size()) {
            for (ArcPoint point : points) {
                if (placed >= blocks.size()) {
                    break;
                }

                int depth = point.depth + tier;
                BlockPos pos = center.offset(
                        forward.getStepX() * depth + right.getStepX() * point.lateral,
                        tier,
                        forward.getStepZ() * depth + right.getStepZ() * point.lateral
                );
                BlockState state = facePlayer(blocks.get(placed).defaultBlockState(), directionToward(pos, center));
                level.setBlock(pos, state, 3);
                placed++;
            }
            tier++;
        }

        int placedCount = placed;
        source.sendSuccess(() -> Component.literal("Placed " + placedCount + " ZPS blocks in a panorama"), true);
        return placed;
    }

    private static List<ArcPoint> quarterArcPoints(int radius) {
        List<ArcPoint> points = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int lateral = -radius; lateral <= radius; lateral++) {
            for (int depth = 1; depth <= radius; depth++) {
                if (Math.abs(lateral) > depth) {
                    continue;
                }

                int distanceSquared = lateral * lateral + depth * depth;
                if (Math.round(Math.sqrt(distanceSquared)) == radius) {
                    points.add(new ArcPoint(lateral, depth, Math.abs(distanceSquared - radiusSquared)));
                }
            }
        }

        points.sort(Comparator
                .comparingDouble((ArcPoint point) -> Math.atan2(point.lateral, point.depth))
                .thenComparingInt(point -> point.distanceError));
        return points;
    }

    private static BlockState facePlayer(BlockState state, Direction direction) {
        DirectionProperty horizontalFacing = BlockStateProperties.HORIZONTAL_FACING;
        if (state.hasProperty(horizontalFacing)) {
            return state.setValue(horizontalFacing, direction);
        }

        DirectionProperty facing = BlockStateProperties.FACING;
        if (state.hasProperty(facing) && facing.getPossibleValues().contains(direction)) {
            return state.setValue(facing, direction);
        }

        return state;
    }

    private static Direction directionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private record ArcPoint(int lateral, int depth, int distanceError) {
    }
}
