package g_mungus.zps.room;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * The separate pockets of air inside one cell, and where each opens onto the cell's faces.
 *
 * <p>A block's shape need not sit against a face to seal: a plate across the middle of the cell,
 * like a closed Large Space Door, splits it into two pockets that each open onto the faces on their
 * own side. Air can then cross into a neighbour only from a pocket, through the part of the face
 * that pocket opens onto, into a pocket of the neighbour that is open over the same part.
 */
final class CellRegions {

    private static final double EPSILON = 1.0e-7;

    /** A cell with nothing in it: one pocket, open over every face. */
    static final CellRegions OPEN = of(List.of(new AABB(0, 0, 0, 1, 1, 1)));
    /** A full block: no air at all. */
    static final CellRegions SOLID = new CellRegions(new double[0][][][]);

    /**
     * By identity, since shapes do not compare by value; most come from a block state's own cache,
     * so there are few distinct ones. Weak keys let shapes built on the fly be collected.
     */
    private static final Cache<VoxelShape, CellRegions> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .build();

    /**
     * For each pocket, for each face (by {@link Direction#ordinal()}), the open rectangles it has
     * there, as {@code {u0, v0, u1, v1}} in the two axes across that face, lowest axis first.
     */
    private final double[][][][] faces;

    private CellRegions(double[][][][] faces) {
        this.faces = faces;
    }

    int count() {
        return faces.length;
    }

    /** The pockets of air in a cell whose collision shape is {@code shape}. */
    static CellRegions of(VoxelShape shape) {
        if (shape.isEmpty()) {
            return OPEN;
        }
        if (shape == Shapes.block()) {
            return SOLID;
        }
        try {
            return CACHE.get(shape, () -> of(Shapes.join(Shapes.block(), shape, BooleanOp.ONLY_FIRST).toAabbs()));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Whether air in pocket {@code region} of this cell reaches pocket {@code otherRegion} of the
     * neighbour on {@code side}: some open part of this cell's face overlaps an open part of theirs.
     */
    boolean connects(int region, Direction side, CellRegions other, int otherRegion) {
        double[][] mine = faces[region][side.ordinal()];
        double[][] theirs = other.faces[otherRegion][side.getOpposite().ordinal()];
        for (double[] a : mine) {
            for (double[] b : theirs) {
                if (Math.min(a[2], b[2]) - Math.max(a[0], b[0]) > EPSILON
                        && Math.min(a[3], b[3]) - Math.max(a[1], b[1]) > EPSILON) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Group the boxes of open space into pockets — boxes sharing part of a face are one — and map their faces. */
    private static CellRegions of(List<AABB> boxes) {
        int[] parent = new int[boxes.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                if (touch(boxes.get(i), boxes.get(j))) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }

        List<List<AABB>> pockets = new ArrayList<>();
        int[] pocketOf = new int[boxes.size()];
        Arrays.fill(pocketOf, -1);
        for (int i = 0; i < boxes.size(); i++) {
            int root = find(parent, i);
            if (pocketOf[root] < 0) {
                pocketOf[root] = pockets.size();
                pockets.add(new ArrayList<>());
            }
            pockets.get(pocketOf[root]).add(boxes.get(i));
        }

        if (pockets.size() > Long.SIZE) {
            // More than the scanner can keep track of; no real block comes close. Treat it as open.
            return OPEN;
        }
        double[][][][] faces = new double[pockets.size()][Direction.values().length][][];
        for (int p = 0; p < pockets.size(); p++) {
            for (Direction side : Direction.values()) {
                List<double[]> rects = new ArrayList<>();
                for (AABB box : pockets.get(p)) {
                    if (reaches(box, side)) {
                        rects.add(across(box, side.getAxis()));
                    }
                }
                faces[p][side.ordinal()] = rects.toArray(new double[0][]);
            }
        }
        return new CellRegions(faces);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Whether two disjoint boxes share part of a face, not just an edge or a corner. */
    private static boolean touch(AABB a, AABB b) {
        double overlapX = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX);
        double overlapY = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);
        double overlapZ = Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ);
        int flat = 0;
        int open = 0;
        for (double overlap : new double[]{overlapX, overlapY, overlapZ}) {
            if (Math.abs(overlap) <= EPSILON) {
                flat++;
            } else if (overlap > EPSILON) {
                open++;
            }
        }
        return flat == 1 && open == 2;
    }

    /** Whether {@code box} runs up to the cell's face on {@code side}. */
    private static boolean reaches(AABB box, Direction side) {
        double edge = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(side.getAxis()) : box.min(side.getAxis());
        double face = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        return Math.abs(edge - face) <= EPSILON;
    }

    /** The box's extent across a face on {@code axis}, in that face's two other axes, lowest first. */
    private static double[] across(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> new double[]{box.minY, box.minZ, box.maxY, box.maxZ};
            case Y -> new double[]{box.minX, box.minZ, box.maxX, box.maxZ};
            case Z -> new double[]{box.minX, box.minY, box.maxX, box.maxY};
        };
    }
}
