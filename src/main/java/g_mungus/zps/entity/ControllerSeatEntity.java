package g_mungus.zps.entity;

import g_mungus.zps.blockentity.ControllerBlockEntity;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
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
     * However the seat goes — its rider leaving, logging out on it, or a command — the controller
     * goes dark. Not when its chunk unloads: the block is unloading with it, and the seat comes back
     * with the chunk to sort it out then.
     */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide && getRemovalReason() != RemovalReason.UNLOADED_TO_CHUNK) {
            release();
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void defineSynchedData() {}

    @Override
    public LivingEntity getControllingPassenger() {
        if (blockEntity != null && !this.getPassengers().isEmpty()) {
            return (LivingEntity) this.getPassengers().get(0);
        }
        return null;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /**
     * Nudges a standing passenger up slightly, so their feet rest on the controller rather than
     * sinking into it. {@code StandingVehicleEntity} keeps them upright; this places them.
     */
    @Override
    public double getPassengersRidingOffset() {
        return super.getPassengersRidingOffset() - 0.025;
    }

    /// Riders stand on the floor already, so they step off exactly where they are instead of popping up onto the seat.
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return passenger.position();
    }
}
