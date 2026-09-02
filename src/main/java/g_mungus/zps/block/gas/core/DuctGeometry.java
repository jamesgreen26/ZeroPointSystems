package g_mungus.zps.block.gas.core;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The profile every gas block on the network is drawn to: a flattened 12x8 box along a horizontal
 * run, a square 10x10 column along a vertical one.
 *
 * <p>Where the two meet the horizontal profile wins for as long as it lasts — the core keeps the
 * full 12x8 box and a vertical arm only narrows to 10x10 once it climbs past it. This is the
 * collision half of the shape; the visual half is assembled from {@code block/gas_duct/*} by each
 * block's blockstate, and the two must be kept in step.
 */
public final class DuctGeometry {

    /** Core of a block on a run that goes anywhere horizontally: the full 12x8 profile. */
    public static final VoxelShape HORIZONTAL_CORE = Block.box(2, 4, 2, 14, 12, 14);
    /** Core of a block on a purely vertical run: the narrower 10x10 column. */
    public static final VoxelShape VERTICAL_CORE = Block.box(3, 4, 3, 13, 12, 13);

    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, EnumProperty<DuctConnectionType>> PROPERTIES =
            new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, Block.box(2, 4, 0, 14, 12, 2));
        ARMS.put(Direction.SOUTH, Block.box(2, 4, 14, 14, 12, 16));
        ARMS.put(Direction.WEST, Block.box(0, 4, 2, 2, 12, 14));
        ARMS.put(Direction.EAST, Block.box(14, 4, 2, 16, 12, 14));
        ARMS.put(Direction.DOWN, Block.box(3, 0, 3, 13, 4, 13));
        ARMS.put(Direction.UP, Block.box(3, 12, 3, 13, 16, 13));

        PROPERTIES.put(Direction.NORTH, GasNodeBlock.NORTH_CONNECTION);
        PROPERTIES.put(Direction.SOUTH, GasNodeBlock.SOUTH_CONNECTION);
        PROPERTIES.put(Direction.EAST, GasNodeBlock.EAST_CONNECTION);
        PROPERTIES.put(Direction.WEST, GasNodeBlock.WEST_CONNECTION);
        PROPERTIES.put(Direction.UP, GasNodeBlock.UP_CONNECTION);
        PROPERTIES.put(Direction.DOWN, GasNodeBlock.DOWN_CONNECTION);
    }

    private DuctGeometry() {
    }

    public static List<EnumProperty<DuctConnectionType>> connectionProperties() {
        return List.copyOf(PROPERTIES.values());
    }

    public static EnumProperty<DuctConnectionType> connectionProperty(Direction direction) {
        return PROPERTIES.get(direction);
    }

    /** The stub that reaches from the core out to one face. */
    public static VoxelShape arm(Direction direction) {
        return ARMS.get(direction);
    }

    public static VoxelShape core(boolean horizontalProfile) {
        return horizontalProfile ? HORIZONTAL_CORE : VERTICAL_CORE;
    }

    /** True when anything joins on a horizontal face, which is what widens the core to 12x8. */
    public static boolean hasHorizontalConnection(BlockState state) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (state.getValue(connectionProperty(direction)) != DuctConnectionType.NONE) {
                return true;
            }
        }
        return false;
    }
}
