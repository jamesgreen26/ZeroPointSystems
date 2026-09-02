package g_mungus.zps.block.gas.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.api.nodes.ILeakNode;
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode;

import java.util.HashSet;

/**
 * A pipe node that bleeds gas out of any face left open.
 *
 * <p>Kelvin removes {@code getLeakRatio} of every gas at the node each tick, so this is read live
 * from the block state rather than fixed when the node is created — a duct only starts leaking when
 * something next to it is blown up.
 */
public class LeakyPipeDuctNode extends PipeDuctNode implements ILeakNode {

    /** Fraction of the node's gas lost per tick, per open face. */
    private static final double LEAK_RATIO_PER_FACE = 0.15;
    /** However many faces are open, some gas still gets through. */
    private static final double MAX_LEAK_RATIO = 0.6;

    public LeakyPipeDuctNode(DuctNodePos pos, double volume, double maxPressure,
                             double maxTemperature, double heatCapacity) {
        super(pos, NodeBehaviorType.PIPE, new HashSet<DuctEdge>(), volume, maxPressure,
                maxTemperature, heatCapacity);
    }

    @Override
    public double getLeakRatio(@NotNull Level level) {
        BlockPos blockPos = BlockPos.containing(getPos().getX(), getPos().getY(), getPos().getZ());
        if (!level.isLoaded(blockPos)) {
            return 0;
        }

        return leakRatioFor(level.getBlockState(blockPos));
    }

    /** Fraction of a node's gas lost per tick given how many of its faces are open. */
    public static double leakRatioFor(BlockState state) {
        return Math.min(countLeakingFaces(state) * LEAK_RATIO_PER_FACE, MAX_LEAK_RATIO);
    }

    public static int countLeakingFaces(BlockState state) {
        if (!(state.getBlock() instanceof DuctBlockFaces faces)) {
            return 0;
        }
        int leaking = 0;
        for (Direction direction : Direction.values()) {
            if (faces.connectionAt(state, direction) == DuctConnectionType.LEAK) {
                leaking++;
            }
        }
        return leaking;
    }

    /** Implemented by blocks that track a {@link DuctConnectionType} per face. */
    public interface DuctBlockFaces {
        DuctConnectionType connectionAt(BlockState state, Direction direction);
    }
}
