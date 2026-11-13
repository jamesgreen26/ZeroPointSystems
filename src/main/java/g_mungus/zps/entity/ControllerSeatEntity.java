package g_mungus.zps.entity;

import g_mungus.zps.blockentity.ControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The invisible spot a player stands on in front of an Octo- or Dodeca-Controller.
 *
 * <p>The seat itself sits inside the controller's block and its rider is put one block out in front.
 * That way it only ever has the one block to look at for its controller, however many others are
 * nearby, and it is always in the controller's chunk — Valkyrien Skies does not tick entities in a
 * ship chunk with no blocks in it.
 *
 * <p>Both sides find the controller behind it: the server to drive and to tell when the seat is
 * orphaned, the rider's client to read which keys the controller is bound to. The keys themselves
 * are read and sent by {@code ControllerClientHooks}. Getting off is using the controller again, not
 * sneaking — see {@code PlayerMixin} and {@code ControllerInteraction}.
 */
public abstract class ControllerSeatEntity extends Entity implements StandingVehicleEntity, RiderOffsetVehicle {
    public @Nullable ControllerBlockEntity<?> blockEntity = null;

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

    /** The controller is the block the seat is in. */
    private boolean findController() {
        BlockEntity found = level().getBlockEntity(blockPosition());
        if (found instanceof ControllerBlockEntity<?> controller && isOwnController(found)) {
            blockEntity = controller;
            return true;
        }
        blockEntity = null;
        return false;
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
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
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
    protected void defineSynchedData() {}

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
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /**
     * Brings a standing passenger's feet down from the seat, which is centred in the controller's
     * block, to the floor that block stands on, nudged up slightly so they rest on it rather than
     * sinking in. {@code StandingVehicleEntity} keeps them upright; this places them.
     */
    @Override
    public double getPassengersRidingOffset() {
        double seatLift = 0.5 - getBbHeight() / 2;
        return super.getPassengersRidingOffset() - 0.025 - seatLift;
    }

    /** One block out from the controller's face, at the height vanilla would put them. */
    @Override
    public @NotNull Vec3 riderPosition(@NotNull Entity passenger, float partialTick) {
        return getPosition(partialTick)
                .add(frontOffset())
                .add(0.0, getPassengersRidingOffset() + passenger.getMyRidingOffset(), 0.0);
    }

    /**
     * Read from the block every time rather than kept: the rider's client positions them before the
     * seat has necessarily ticked, and the block is there on both sides.
     */
    private Vec3 frontOffset() {
        BlockState state = level().getBlockState(blockPosition());
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return Vec3.ZERO;
        }
        return Vec3.atLowerCornerOf(state.getValue(HorizontalDirectionalBlock.FACING).getNormal());
    }

    /** Through {@link #riderPosition}; Valkyrien Skies skips this on ships and asks that directly. */
    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        if (hasPassenger(passenger)) {
            Vec3 spot = riderPosition(passenger, 1.0f);
            callback.accept(passenger, spot.x, spot.y, spot.z);
        }
    }

    /// Riders stand on the floor already, so they step off where they are instead of popping up onto the seat —
    /// a hair above it, so that stepping off onto a ship's floor does not leave them sunk into it.
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return passenger.position().add(0.0, 1.0e-4, 0.0);
    }
}
