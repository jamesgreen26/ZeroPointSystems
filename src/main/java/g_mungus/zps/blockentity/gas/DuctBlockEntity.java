package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.LeakyPipeDuctNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.GasJet;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DuctBlockEntity extends GasNodeBlockEntity {

    /** Kilograms of escaping gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.01;
    /** A leak is a split pipe, not a nozzle: a wider, lazier plume than the vent's jet. */
    private static final double JET_RADIUS = 0.2;
    private static final double JET_OFFSET = 0.45;

    public DuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUCT.get(), pos, state);
    }

    /** Only ticks while a face is open — see {@code DuctBlock.getTicker}. */
    public void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            spawnLeakPlumes();
            return;
        }
        syncNodeState();
    }

    /**
     * Gas escaping per tick, which is what the plume is drawn from.
     *
     * <p>Kelvin's solver does the removal itself through {@code ILeakNode}, taking this fraction of
     * whatever is at the node, so the rate can be derived rather than measured.
     */
    @Override
    protected double massForSync() {
        if (level == null || level.isClientSide()) {
            return super.massForSync();
        }
        return getGasMass() * LeakyPipeDuctNode.leakRatioFor(getBlockState());
    }

    private void spawnLeakPlumes() {
        // On the client getGasMass() is the synced figure, which for a duct is its leak rate.
        double rate = getGasMass();
        if (rate <= 0 || level == null) {
            return;
        }

        BlockState state = getBlockState();
        int leaking = LeakyPipeDuctNode.countLeakingFaces(state);
        if (leaking <= 0) {
            return;
        }

        // The escaping gas is shared between however many holes there are.
        double perFace = rate / leaking / MASS_PER_PARTICLE;
        double speed = Mth.clamp(0.0004 * Math.pow(getPressure(), 0.4), 0.01, 0.35);
        Vec3 centre = Vec3.atCenterOf(worldPosition);

        for (Direction direction : Direction.values()) {
            if (!(state.getBlock() instanceof LeakyPipeDuctNode.DuctBlockFaces faces)
                    || faces.connectionAt(state, direction) != DuctConnectionType.LEAK) {
                continue;
            }
            GasJet.spawn(level, getDuctNodePosition(), ModGases.FLUX, centre, direction,
                    speed, perFace, JET_RADIUS, JET_OFFSET);
        }
    }
}
