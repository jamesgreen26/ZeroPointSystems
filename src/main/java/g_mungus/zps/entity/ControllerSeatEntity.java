package g_mungus.zps.entity;

import g_mungus.zps.blockentity.ControllerBlockEntity;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The invisible spot a player stands on in front of an Octo- or Dodeca-Controller.
 *
 * <p>Both sides find the controller behind it: the server to drive and to tell when the seat is
 * orphaned, the rider's client to read which keys the controller is bound to. The keys themselves
 * are read and sent by {@code ControllerClientHooks}. Getting off is using the controller again, not
 * sneaking — see {@code PlayerMixin} and {@code ControllerInteraction}.
 */
public abstract class ControllerSeatEntity extends Entity implements StandingVehicleEntity {
    public @Nullable ControllerBlockEntity<?> blockEntity = null;
    public Vec3i offset = Vec3i.ZERO;

    private static final Vec3i[] ADJACENT_OFFSETS = {
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1)
    };

    protected ControllerSeatEntity(@NotNull EntityType<? extends ControllerSeatEntity> type, @NotNull Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;
    }

    /** Whether this is the kind of controller this seat belongs in front of. */
    protected abstract boolean isOwnController(BlockEntity blockEntity);

    @Override
    public void tick() {
        this.baseTick();

        boolean linked = findController();
        if (level().isClientSide) return;
        if (linked && !getPassengers().isEmpty()) {
            // Every tick rather than on boarding: a rider who logged out on the seat comes back on it.
            blockEntity.setActive(true);
        } else {
            cleanupAndDiscard();
        }
    }

    private boolean findController() {
        if (isFacingController(offset)) {
            return true;
        }
        for (Vec3i testOffset : ADJACENT_OFFSETS) {
            if (isFacingController(testOffset)) {
                offset = testOffset;
                return true;
            }
        }
        blockEntity = null;
        return false;
    }

    private boolean isFacingController(Vec3i testOffset) {
        BlockEntity found = level().getBlockEntity(blockPosition().offset(testOffset));
        if (!(found instanceof ControllerBlockEntity<?> controller) || !isOwnController(found)) {
            return false;
        }
        BlockState state = found.getBlockState();
        if (!state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().getNormal().equals(testOffset)) {
            return false;
        }
        blockEntity = controller;
        return true;
    }

    private void cleanupAndDiscard() {
        release();
        this.discard();
    }

    private void release() {
        if (blockEntity != null) {
            blockEntity.setPressed(0);
            blockEntity.setActive(false);
        }
    }

    /**
     * When the seat really goes — its rider leaving, logging out on it, or a command — the controller
     * goes dark. Forge also calls this when the seat's chunk merely stops being tracked, with no
     * removal reason, from inside the chunk loading update, where driving the network could load a
     * chunk and corrupt it. So not then, nor when the chunk unloads (the seat comes back with it to
     * sort things out), nor when it changes dimension (its copy on the far side finds the controller
     * there, and a grid mod moving a ship, as Genesis does, may be partway through moving the chunk).
     */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        RemovalReason reason = getRemovalReason();
        if (!level().isClientSide && reason != null
                && (reason.shouldDestroy() || reason == RemovalReason.UNLOADED_WITH_PLAYER)) {
            release();
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {}

    /**
     * The rider steers nothing: keys go to the controller over the network. A controlling rider's
     * client would also send the seat's position, which on a Valkyrien Skies ship drags it out of
     * the shipyard and away from its controller.
     */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public @NotNull Vec3 getPassengerRidingPosition(@NotNull Entity passenger) {
        return super.getPassengerRidingPosition(passenger).add(0, 0.1, 0);
    }

    /// Riders stand on the floor already, so they step off exactly where they are instead of popping up onto the seat.
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return passenger.position();
    }
}
