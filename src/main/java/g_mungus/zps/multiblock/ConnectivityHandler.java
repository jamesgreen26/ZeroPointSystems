package g_mungus.zps.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Forms and dissolves {@link MultiblockPart} structures.
 * <p>
 * A structure is a {@code width x width x length} box of parts sharing one block entity type, with the controller
 * at the minimum corner. {@link #formMulti(BlockEntity)} searches around a part for the largest structure it can
 * head and builds it; {@link #splitMulti(BlockEntity)} dissolves the structure a part belongs to, handing the
 * pooled contents back out to the individual parts.
 * <p>
 * Adapted from Create's {@code ConnectivityHandler} (MIT), with the fluid-specific merging replaced by the
 * generic contents hooks on {@link MultiblockPart}.
 */
public final class ConnectivityHandler {

    private ConnectivityHandler() {
    }

    public static <T extends BlockEntity & MultiblockPart> void formMulti(T be) {
        SearchCache<T> cache = new SearchCache<>();
        List<T> frontier = new ArrayList<>();
        frontier.add(be);
        formMulti(be.getType(), be.getLevel(), cache, frontier);
    }

    private static <T extends BlockEntity & MultiblockPart> void formMulti(BlockEntityType<?> type, BlockGetter level,
                                                                          SearchCache<T> cache, List<T> frontier) {
        PriorityQueue<Candidate<T>> creationQueue = new PriorityQueue<>((one, two) -> two.amount() - one.amount());
        Set<BlockPos> visited = new HashSet<>();
        Direction.Axis mainAxis = frontier.get(0).getMainConnectionAxis();

        // A vertical structure's search is not restricted along Y; a horizontal one is not restricted along X/Z.
        int minX = mainAxis == Direction.Axis.Y ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int minY = mainAxis != Direction.Axis.Y ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int minZ = mainAxis == Direction.Axis.Y ? Integer.MAX_VALUE : Integer.MIN_VALUE;

        for (T be : frontier) {
            BlockPos pos = be.getBlockPos();
            minX = Math.min(pos.getX(), minX);
            minY = Math.min(pos.getY(), minY);
            minZ = Math.min(pos.getZ(), minZ);
        }
        int maxWidth = frontier.get(0).getMaxWidth();
        if (mainAxis == Direction.Axis.Y) {
            minX -= maxWidth;
            minZ -= maxWidth;
        } else {
            minY -= maxWidth;
        }

        while (!frontier.isEmpty()) {
            T part = frontier.remove(0);
            BlockPos partPos = part.getBlockPos();
            if (!visited.add(partPos)) {
                continue;
            }

            int amount = tryToFormNewMulti(part, cache, true);
            if (amount > 1) {
                creationQueue.add(new Candidate<>(amount, part));
            }

            for (Direction.Axis axis : Direction.Axis.values()) {
                Direction dir = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
                BlockPos next = partPos.relative(dir);

                if (next.getX() <= minX || next.getY() <= minY || next.getZ() <= minZ) {
                    continue;
                }
                if (visited.contains(next)) {
                    continue;
                }
                T nextBe = partAt(type, level, next);
                if (nextBe == null || nextBe.isRemoved()) {
                    continue;
                }
                frontier.add(nextBe);
            }
        }
        visited.clear();

        while (!creationQueue.isEmpty()) {
            T toCreate = creationQueue.poll().part();
            if (!visited.add(toCreate.getBlockPos())) {
                continue;
            }
            tryToFormNewMulti(toCreate, cache, false);
        }
    }

    private static <T extends BlockEntity & MultiblockPart> int tryToFormNewMulti(T be, SearchCache<T> cache,
                                                                                 boolean simulate) {
        int bestWidth = 1;
        int bestAmount = -1;
        if (!be.isController()) {
            return 0;
        }

        int radius = be.getMaxWidth();
        for (int w = 1; w <= radius; w++) {
            int amount = tryToFormNewMultiOfWidth(be, w, cache, true);
            if (amount < bestAmount) {
                continue;
            }
            bestWidth = w;
            bestAmount = amount;
        }

        if (!simulate) {
            int beWidth = be.getWidth();
            if (beWidth == bestWidth && beWidth * beWidth * be.getHeight() == bestAmount) {
                return bestAmount;
            }

            splitMultiAndInvalidate(be, cache, false);
            be.setContainerSize(bestAmount);

            tryToFormNewMultiOfWidth(be, bestWidth, cache, false);

            be.preventConnectivityUpdate();
            be.setWidth(bestWidth);
            be.setHeight(bestAmount / bestWidth / bestWidth);
            be.notifyMultiUpdated();
        }
        return bestAmount;
    }

    private static <T extends BlockEntity & MultiblockPart> int tryToFormNewMultiOfWidth(T be, int width,
                                                                                        SearchCache<T> cache,
                                                                                        boolean simulate) {
        int amount = 0;
        int height = 0;
        BlockEntityType<?> type = be.getType();
        Level level = be.getLevel();
        if (level == null) {
            return 0;
        }
        BlockPos origin = be.getBlockPos();
        Direction.Axis axis = be.getMainConnectionAxis();

        Search:
        for (int yOffset = 0; yOffset < be.getMaxLength(axis, width); yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = offset(origin, axis, xOffset, yOffset, zOffset);
                    Optional<T> part = cache.getOrCache(type, level, pos);
                    if (part.isEmpty()) {
                        break Search;
                    }

                    T controller = part.get();
                    int otherWidth = controller.getWidth();
                    if (otherWidth > width) {
                        break Search;
                    }
                    if (otherWidth == width && controller.getHeight() == be.getMaxLength(axis, width)) {
                        break Search;
                    }
                    if (axis != controller.getMainConnectionAxis()) {
                        break Search;
                    }

                    BlockPos conPos = controller.getBlockPos();
                    if (!conPos.equals(origin)) {
                        if (axis == Direction.Axis.Y) {
                            // vertical structure (the existing one must fit inside the new footprint)
                            if (conPos.getX() < origin.getX() || conPos.getZ() < origin.getZ()) {
                                break Search;
                            }
                            if (conPos.getX() + otherWidth > origin.getX() + width) {
                                break Search;
                            }
                            if (conPos.getZ() + otherWidth > origin.getZ() + width) {
                                break Search;
                            }
                        } else {
                            // horizontal structure
                            if (axis == Direction.Axis.Z && conPos.getX() < origin.getX()) {
                                break Search;
                            }
                            if (conPos.getY() < origin.getY()) {
                                break Search;
                            }
                            if (axis == Direction.Axis.X && conPos.getZ() < origin.getZ()) {
                                break Search;
                            }
                            if (axis == Direction.Axis.Z && conPos.getX() + otherWidth > origin.getX() + width) {
                                break Search;
                            }
                            if (conPos.getY() + otherWidth > origin.getY() + width) {
                                break Search;
                            }
                            if (axis == Direction.Axis.X && conPos.getZ() + otherWidth > origin.getZ() + width) {
                                break Search;
                            }
                        }
                    }
                    if (!be.canMergeWith(controller)) {
                        break Search;
                    }
                }
            }
            amount += width * width;
            height++;
        }

        if (simulate) {
            return amount;
        }

        Object extraData = be.getExtraData();

        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = offset(origin, axis, xOffset, yOffset, zOffset);
                    T part = partAt(type, level, pos);
                    if (part == null || part == be) {
                        continue;
                    }

                    extraData = be.modifyExtraData(extraData);

                    // A part that heads a smaller structure carries all of its contents; take them before it is
                    // dissolved so nothing is handed back out to blocks that are about to join us anyway.
                    be.absorbContents(part);

                    splitMultiAndInvalidate(part, cache, false);
                    part.setController(origin);
                    part.preventConnectivityUpdate();
                    cache.put(pos, be);
                    part.setHeight(height);
                    part.setWidth(width);
                    part.notifyMultiUpdated();
                }
            }
        }
        be.setExtraData(extraData);
        be.notifyMultiUpdated();
        return amount;
    }

    public static <T extends BlockEntity & MultiblockPart> void splitMulti(T be) {
        splitMultiAndInvalidate(be, null, false);
    }

    /** {@code tryReconnect} re-forms the leftovers straight away, useful when only a few parts were removed. */
    private static <T extends BlockEntity & MultiblockPart> void splitMultiAndInvalidate(T be,
                                                                                        @Nullable SearchCache<T> cache,
                                                                                        boolean tryReconnect) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        be = be.getControllerBE();
        if (be == null) {
            return;
        }

        int height = be.getHeight();
        int width = be.getWidth();
        if (width == 1 && height == 1) {
            return;
        }

        BlockPos origin = be.getBlockPos();
        List<T> frontier = new ArrayList<>();
        Direction.Axis axis = be.getMainConnectionAxis();

        Object toDistribute = be.takeSplitContents();

        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = offset(origin, axis, xOffset, yOffset, zOffset);
                    T partAt = partAt(be.getType(), level, pos);
                    if (partAt == null) {
                        continue;
                    }
                    if (!partAt.getController().equals(origin)) {
                        continue;
                    }

                    T controllerBE = partAt.getControllerBE();
                    partAt.setExtraData(controllerBE == null ? null : controllerBE.getExtraData());
                    partAt.removeController(true);

                    if (partAt != be) {
                        toDistribute = partAt.receiveSplitContents(toDistribute);
                    }
                    if (tryReconnect) {
                        frontier.add(partAt);
                        partAt.preventConnectivityUpdate();
                    }
                    if (cache != null) {
                        cache.put(pos, partAt);
                    }
                }
            }
        }

        level.invalidateCapabilities(be.getBlockPos());

        if (tryReconnect) {
            formMulti(be.getType(), level, cache == null ? new SearchCache<>() : cache, frontier);
        }
    }

    private static BlockPos offset(BlockPos origin, Direction.Axis axis, int xOffset, int yOffset, int zOffset) {
        return switch (axis) {
            case X -> origin.offset(yOffset, xOffset, zOffset);
            case Y -> origin.offset(xOffset, yOffset, zOffset);
            case Z -> origin.offset(xOffset, zOffset, yOffset);
        };
    }

    @Nullable
    public static <T extends BlockEntity & MultiblockPart> T partAt(BlockEntityType<?> type, BlockGetter level,
                                                                    BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && be.getType() == type && !be.isRemoved()) {
            return checked(be);
        }
        return null;
    }

    public static boolean isConnected(BlockGetter level, BlockPos pos, BlockPos other) {
        MultiblockPart one = checked(level.getBlockEntity(pos));
        MultiblockPart two = checked(level.getBlockEntity(other));
        if (one == null || two == null) {
            return false;
        }
        return one.getController().equals(two.getController());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity & MultiblockPart> T checked(@Nullable BlockEntity be) {
        if (be instanceof MultiblockPart) {
            return (T) be;
        }
        return null;
    }

    private record Candidate<T>(int amount, T part) {
    }

    private static class SearchCache<T extends BlockEntity & MultiblockPart> {
        private final Map<BlockPos, Optional<T>> controllerMap = new HashMap<>();

        void put(BlockPos pos, T target) {
            controllerMap.put(pos, Optional.of(target));
        }

        void putEmpty(BlockPos pos) {
            controllerMap.put(pos, Optional.empty());
        }

        boolean hasVisited(BlockPos pos) {
            return controllerMap.containsKey(pos);
        }

        Optional<T> getOrCache(BlockEntityType<?> type, BlockGetter level, BlockPos pos) {
            if (hasVisited(pos)) {
                return controllerMap.get(pos);
            }

            T partAt = partAt(type, level, pos);
            if (partAt == null) {
                putEmpty(pos);
                return Optional.empty();
            }
            T controller = checked(level.getBlockEntity(partAt.getController()));
            if (controller == null) {
                putEmpty(pos);
                return Optional.empty();
            }
            put(pos, controller);
            return Optional.of(controller);
        }
    }
}
