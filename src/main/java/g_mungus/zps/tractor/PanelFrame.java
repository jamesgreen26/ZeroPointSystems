package g_mungus.zps.tractor;

import net.minecraft.core.Direction;

/**
 * Which world directions are "up" and "right" on a Beam Collector panel, as seen by someone looking into its mouth.
 * <p>
 * The block model is authored facing north (mouth on -Z, up +Y, right +X) and the blockstate rotates it per
 * facing, so these directions are simply where the model's +Y and +X end up after that rotation. The border
 * flags on the block state are expressed in this frame, which is what lets one set of edge models serve all six
 * facings.
 */
public record PanelFrame(Direction up, Direction right) {

    public static PanelFrame of(Direction facing) {
        return switch (facing) {
            case NORTH -> new PanelFrame(Direction.UP, Direction.EAST);
            case EAST -> new PanelFrame(Direction.UP, Direction.SOUTH);
            case SOUTH -> new PanelFrame(Direction.UP, Direction.WEST);
            case WEST -> new PanelFrame(Direction.UP, Direction.NORTH);
            // x=90 tips the mouth from north to down, which carries the model's top round to the north.
            case DOWN -> new PanelFrame(Direction.NORTH, Direction.EAST);
            // x=270 tips it the other way.
            case UP -> new PanelFrame(Direction.SOUTH, Direction.EAST);
        };
    }

    public Direction down() {
        return up.getOpposite();
    }

    public Direction left() {
        return right.getOpposite();
    }
}
