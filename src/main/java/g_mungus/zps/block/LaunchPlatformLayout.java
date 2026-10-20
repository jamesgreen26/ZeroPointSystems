package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The volume a Launch Platform lifts: the largest rectangle of platform blocks around it that has an upright Space
 * Truss tower beside one of its sides, as tall as the tallest such tower. Shared by the server, which assembles it, and
 * the client, which outlines it.
 *
 * @param min    the rectangle's lowest corner, on the platform layer
 * @param max    the rectangle's highest corner, on the platform layer
 * @param height how many blocks above the platform the volume reaches
 */
public record LaunchPlatformLayout(BlockPos min, BlockPos max, int height) {

    public static final int MAX_WIDTH = 64;
    public static final int MAX_HEIGHT = 64;

    /** How far either way along x and z the search looks; far enough to see a rectangle wider than allowed. */
    private static final int REACH = MAX_WIDTH;

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    /** Too big rectangles or towers fail outright rather than lifting some smaller part. */
    public boolean tooBig() {
        return sizeX() > MAX_WIDTH || sizeZ() > MAX_WIDTH || height > MAX_HEIGHT;
    }

    /** The blocks that go up, which leave the platform itself behind. */
    public BoundingBox volume() {
        return new BoundingBox(min.getX(), min.getY() + 1, min.getZ(), max.getX(), min.getY() + height, max.getZ());
    }

    public AABB volumeAABB() {
        return AABB.of(volume());
    }

    /** The layout for the platform at origin, or null when no rectangle around it has a tower beside it. */
    public static @Nullable LaunchPlatformLayout find(BlockGetter level, BlockPos origin) {
        return new Search(level, origin).run();
    }

    private static final class Search {
        private static final int SIZE = 2 * REACH + 1;

        private final BlockGetter level;
        private final BlockPos origin;
        /** Per row along z, how many platform blocks lie before each index along x. */
        private final int[][] platformsBefore = new int[SIZE][SIZE + 1];
        /** Tower heights one ring wider than the window, so the cells beside an edge rectangle fit; -1 unread. */
        private final int[][] towerHeights = new int[SIZE + 2][SIZE + 2];

        Search(BlockGetter level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int k = 0; k < SIZE; k++) {
                for (int i = 0; i < SIZE; i++) {
                    pos.set(origin.getX() + i - REACH, origin.getY(), origin.getZ() + k - REACH);
                    boolean platform = level.getBlockState(pos).is(ModBlocks.LAUNCH_PLATFORM.get());
                    platformsBefore[k][i + 1] = platformsBefore[k][i] + (platform ? 1 : 0);
                }
            }
            for (int[] row : towerHeights) {
                Arrays.fill(row, -1);
            }
        }

        @Nullable LaunchPlatformLayout run() {
            // Shrinking a rectangle never brings a tower beside it, so the answer is one that cannot grow; every
            // such rectangle is the full run along z of some span along x, and the spans are all tried here.
            int centre = REACH;
            int bestArea = 0;
            LaunchPlatformLayout best = null;
            for (int i0 = centre; i0 >= 0 && allPlatform(centre, i0, centre); i0--) {
                for (int i1 = centre; i1 < SIZE && allPlatform(centre, i0, i1); i1++) {
                    int k0 = centre;
                    while (k0 > 0 && allPlatform(k0 - 1, i0, i1)) k0--;
                    int k1 = centre;
                    while (k1 < SIZE - 1 && allPlatform(k1 + 1, i0, i1)) k1++;

                    int area = (i1 - i0 + 1) * (k1 - k0 + 1);
                    if (area <= bestArea) continue;
                    int height = tallestTowerBeside(i0, i1, k0, k1);
                    if (height == 0) continue;

                    bestArea = area;
                    best = new LaunchPlatformLayout(
                            origin.offset(i0 - REACH, 0, k0 - REACH),
                            origin.offset(i1 - REACH, 0, k1 - REACH),
                            height);
                }
            }
            return best;
        }

        private boolean allPlatform(int k, int i0, int i1) {
            return platformsBefore[k][i1 + 1] - platformsBefore[k][i0] == i1 - i0 + 1;
        }

        /** Along each side, not at the corners. */
        private int tallestTowerBeside(int i0, int i1, int k0, int k1) {
            int tallest = 0;
            for (int i = i0; i <= i1; i++) {
                tallest = Math.max(tallest, Math.max(towerHeight(i, k0 - 1), towerHeight(i, k1 + 1)));
            }
            for (int k = k0; k <= k1; k++) {
                tallest = Math.max(tallest, Math.max(towerHeight(i0 - 1, k), towerHeight(i1 + 1, k)));
            }
            return tallest;
        }

        /** Trusses laid sideways do not make a tower. */
        private static boolean isUprightTruss(BlockState state) {
            return state.is(ModBlocks.SPACE_TRUSS.get()) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        }

        /** How many upright trusses stand above a truss on the platform layer, or 0 when there is none there. */
        private int towerHeight(int i, int k) {
            int cached = towerHeights[i + 1][k + 1];
            if (cached >= 0) return cached;

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                    origin.getX() + i - REACH, origin.getY(), origin.getZ() + k - REACH);
            int height = 0;
            if (isUprightTruss(level.getBlockState(pos))) {
                int top = level.getMaxBuildHeight();
                while (pos.getY() + 1 < top && isUprightTruss(level.getBlockState(pos.move(0, 1, 0)))) {
                    height++;
                }
            }
            towerHeights[i + 1][k + 1] = height;
            return height;
        }
    }
}
