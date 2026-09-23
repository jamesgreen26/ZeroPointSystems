package g_mungus.zps.entity;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.block.gas.core.DuctTravelNetwork;
import g_mungus.zps.blockentity.gas.VentBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.GasExposure;
import g_mungus.zps.networking.DuctTravelStateS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A player riding the inside of a duct run.
 *
 * <p>The player is not really moved along the pipe; they are a pair of eyes parked in one vent's
 * grille at a time, and cycling hops those eyes to another vent on the same run. That is the Among
 * Us shape of it, and it means the whole feature is one invisible vehicle plus a destination list.
 *
 * <p>Built on the same invisible-seat idea as {@link OctoMountingEntity}: no physics, no collision,
 * no renderer of its own. The rider is shrunk to {@link #RIDER_DIMENSIONS} and drawn by
 * {@code DuctTravelClientHooks} as a head alone, sitting in the grille. Exiting is plain vanilla
 * dismount — sneak — routed through {@link #getDismountLocationForPassenger} so the player lands in
 * front of whichever grille they were last peeking out of.
 *
 * <p>{@link #ventPos} is the vent's position in the level, which for a vent on a Sable sublevel or a
 * Valkyrien Skies ship is a position in that grid's own block space, far from where the vent
 * appears. The vehicle lives in that same space, parked in the vent like a seat bolted to it, and
 * moves with the grid for free: this entity type is tagged {@code sable:retain_in_sub_level}, the
 * same as the mounting seats, so Sable keeps it there rather than kicking it out into the world on
 * spawn, and carries its rider through the grid's pose itself. What must not stay in grid space is
 * anything handed to the world directly — the spot a rider is put down on and the sounds a vent
 * makes — and those go through {@link #toWorld}. A vehicle that is half in and half out, kicked to
 * world space on spawn and then parked back at grid coordinates by a hop, is one Sable handles on
 * the wrong footing for its rider, and is where riders were being lost.
 */
public class DuctTravelEntity extends Entity {

    /** The rider is a head and nothing else, so their box is one. */
    public static final float HEAD_SIZE = 0.5f;

    /**
     * How far out of the vent's block centre the head sits: to the outer face of the plate, then
     * half a head to clear it. The plate is pressed back against the duct feeding it, so its face
     * sits inside the vent's own block and the head does too. The result is a head cube resting
     * flush against the outside of the grille, centred on it, which is what anyone walking past
     * sees.
     */
    private static final double HEAD_OFFSET = VentBlock.PLATE_THICKNESS / 16.0 - 0.5 + HEAD_SIZE / 2.0;

    /**
     * What the rider is while they are in here. The eye sits at the middle of the cube, so the
     * camera, the hitbox and the head model all centre on the same point.
     */
    public static final EntityDimensions RIDER_DIMENSIONS =
            EntityDimensions.fixed(HEAD_SIZE, HEAD_SIZE).withEyeHeight(HEAD_SIZE / 2.0f);

    private static final String VENT_TAG = "Vent";

    /**
     * Which way the vent currently being looked out of opens. Synched because everyone rendering
     * the rider needs it, not just the rider: a head hanging out of a ceiling is drawn upside down,
     * and onlookers are the ones who see that.
     */
    private static final EntityDataAccessor<Direction> VENT_FACING =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.DIRECTION);

    /**
     * Whether the rider is between vents rather than sitting in one. Synched for the same reason
     * the facing is: it decides whether anyone draws them at all.
     */
    private static final EntityDataAccessor<Boolean> TRAVELLING =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Ticks between asking for another vent and actually being moved there. The client spends them
     * fading to black, so the move lands unseen; the server counts from when the request arrived,
     * which is at or after the client began fading, so it is always covered.
     */
    public static final int FADE_OUT_TICKS = 6;
    /** Ticks the client spends fading back in once the move has landed. */
    public static final int FADE_IN_TICKS = 8;

    /** The clatter a vent makes when someone goes through it. */
    public static final SoundEvent CLANK_SOUND = SoundEvents.IRON_TRAPDOOR_OPEN;
    public static final float CLANK_VOLUME = 0.7f;
    public static final float CLANK_PITCH = 0.6f;

    /**
     * The longest a journey may take, however far apart the two vents are. Without a ceiling, a
     * pair of vents at opposite ends of a world would black a player's screen out for minutes.
     */
    public static final int MAX_TRAVEL_TICKS = 100;

    /**
     * How often, in ticks, the destination list is checked against the run while the rider is
     * simply sitting in a vent. A hop checks it anyway; this is for the run being altered under
     * a rider who is not going anywhere, so the count on their HUD does not go stale.
     */
    public static final int REFRESH_INTERVAL_TICKS = 40;

    /**
     * How far the vent may be from the vehicle, in blocks, before its position is not to be trusted
     * for putting a rider down. The vehicle sits at most a head's length from the vent it is in, so
     * anything beyond a couple of blocks means one of them has been moved without the other: a
     * sublevel taken away throws its entities out into the world and leaves the vent's grid position
     * pointing at nothing.
     */
    private static final double LOST_VENT_DISTANCE = 3.0;

    /** The vent currently being looked out of. */
    private BlockPos ventPos = BlockPos.ZERO;
    /** Index 0 is always the vent the player entered by. Server side; null until first needed. */
    private @Nullable List<BlockPos> destinations;
    private int selected;
    /** Ticks left before the requested hop happens, or 0 when none is pending. */
    private int hopDelay;
    /** Ticks left of the crawl between the two vents, or 0 when not travelling. */
    private int arriveDelay;
    /** Which way the pending hop goes. */
    private int pendingDelta;
    /** Ticks left before another hop may be asked for. Covers the fade back in as well. */
    private int cooldown;
    /** Ticks until the destination list is next checked against the run unprompted. */
    private int refreshDelay = REFRESH_INTERVAL_TICKS;

    public DuctTravelEntity(@NotNull EntityType<DuctTravelEntity> type, @NotNull Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;
    }

    // --- entry ------------------------------------------------------------------------------

    /**
     * Put a player inside the duct run at {@code ventPos}. Returns false, having changed nothing,
     * if the vehicle could not be spawned or the player refused to ride it.
     */
    public static boolean enter(ServerLevel level, BlockPos ventPos, Player player) {
        DuctTravelEntity entity = ModEntities.DUCT_TRAVEL.get().create(level);
        if (entity == null) {
            return false;
        }

        BlockState state = level.getBlockState(ventPos);
        entity.ventPos = ventPos.immutable();
        entity.destinations = DuctTravelNetwork.reachableVents(level, ventPos);
        entity.selected = 0;
        entity.placeAtVent(state);

        if (!level.addFreshEntity(entity)) {
            return false;
        }
        if (!player.startRiding(entity, true)) {
            entity.discard();
            return false;
        }

        clank(level, ventPos, state);
        entity.sendState(player, true);
        return true;
    }

    // --- cycling ----------------------------------------------------------------------------

    /**
     * Take a request for another vent. The move itself waits {@link #FADE_OUT_TICKS} so it happens
     * behind a black screen, and nothing further is accepted until the fade back in would have
     * finished — which is the rate limit on duct travel, enforced here rather than trusted to the
     * client.
     */
    public void requestCycle(int delta) {
        if (hopDelay > 0 || arriveDelay > 0 || cooldown > 0) {
            return;
        }
        pendingDelta = delta;
        hopDelay = FADE_OUT_TICKS;
        cooldown = FADE_OUT_TICKS + FADE_IN_TICKS;
    }

    /**
     * Move one step through the destination list and hop the rider's eyes to the vent that lands
     * on. The list is first brought up to date with what the run reaches now; if nothing but the
     * rider's own vent survives, they stay put.
     */
    private void cycle(int delta, Player player) {
        List<BlockPos> current = refreshDestinations();
        if (current.size() <= 1) {
            sendState(player, false);
            return;
        }

        BlockPos from = ventPos;
        for (int attempt = 0; attempt < current.size(); attempt++) {
            selected = Math.floorMod(selected + delta, current.size());
            BlockPos target = current.get(selected);
            BlockState state = level().getBlockState(target);
            if (state.getBlock() instanceof VentBlock && !VentBlock.isShut(state)
                    && !Compat.isOrphanedGridPos(level(), target)) {
                hopTo(target, state, from, player);
                return;
            }
        }

        // Every vent on the list has gone. Start again from where we actually are.
        destinations = null;
        selected = 0;
        sendState(player, false);
    }

    private void hopTo(BlockPos target, BlockState state, BlockPos from, Player player) {
        ventPos = target.immutable();
        placeAtVent(state);

        // Bring the rider along in the same tick, so the server never briefly believes the player
        // is standing where they came from.
        snapPassengers();

        // Only the vent being left goes off now. The one ahead announces itself when the player
        // actually gets there, which is a journey later.
        //
        // The rider is excluded: they are already standing at the far end of the run, well out of
        // earshot of a vent they may have left hundreds of blocks back. Their client plays them
        // their own copy the moment the screen starts to darken, so what they hear is the vent
        // they are climbing into rather than a sound lost to distance.
        if (level() instanceof ServerLevel server) {
            clank(server, from, server.getBlockState(from), player);
        }

        // The move itself is instant, but the screen stays black for as long as crawling the
        // distance would take. Nothing is sent until then: the state packet is what tells the
        // client it has arrived and may fade back in.
        arriveDelay = travelTicks(from, target);
        cooldown = arriveDelay + FADE_IN_TICKS;
        entityData.set(TRAVELLING, true);
    }

    /**
     * How long the crawl between two vents takes.
     *
     * <p>Straight-line distance, not the length of the pipe run: it is what a player can judge by
     * eye from the coordinates on the HUD, and it does not cost a second search of the network on
     * every hop.
     */
    public static int travelTicks(BlockPos from, BlockPos to) {
        double blocks = Math.sqrt(from.distSqr(to));
        int ticks = (int) Math.ceil(blocks / ZPSConfig.ductTravelBlocksPerTick());
        return Mth.clamp(ticks, 1, MAX_TRAVEL_TICKS);
    }

    private List<BlockPos> ensureDestinations() {
        if (destinations == null) {
            destinations = DuctTravelNetwork.reachableVents(level(), ventPos);
            selected = 0;
        }
        return destinations;
    }

    /**
     * The destination list brought up to date with what the rider can actually get to.
     *
     * <p>The list is drawn up on the way in and kept, so that cycling steps through the same vents
     * in the same order however far the rider has gone. But a duct run is only a list of vents for
     * as long as it holds together: break it behind a rider and the vents beyond the break are as
     * far away as any other, whatever the list says; mend it, or join another run on, and there
     * are vents to offer that the list has never heard of. So the network is walked again from the
     * vent the rider is in before every hop. What is still reachable keeps its place, what is not
     * is dropped, and what is newly reachable is added after the rest, nearest the way in first.
     * The selection follows the vent the rider is in.
     */
    private List<BlockPos> refreshDestinations() {
        List<BlockPos> current = ensureDestinations();
        Set<BlockPos> reachable = DuctTravelNetwork.reachableVentSet(level(), ventPos);

        List<BlockPos> kept = new ArrayList<>(current.size());
        for (BlockPos vent : current) {
            if (reachable.contains(vent)) {
                kept.add(vent);
            }
        }
        // Distances are measured from the vent the player came in by while it is still on the run,
        // as the original list's were, so the new entries sort the way the old ones did.
        BlockPos origin = kept.isEmpty() ? ventPos : kept.get(0);
        List<BlockPos> added = new ArrayList<>();
        for (BlockPos vent : reachable) {
            if (!current.contains(vent)) {
                added.add(vent);
            }
        }
        added.sort(Comparator.comparingDouble(vent -> vent.distSqr(origin)));

        if (added.isEmpty() && kept.size() == current.size()) {
            return current;
        }
        for (BlockPos vent : added) {
            if (kept.size() > DuctTravelNetwork.MAX_DESTINATIONS) {
                break;
            }
            kept.add(vent);
        }
        destinations = List.copyOf(kept);
        selected = Math.max(0, destinations.indexOf(ventPos));
        return destinations;
    }

    /**
     * Push the destination list to the rider.
     *
     * <p>{@code orient} turns the player to look straight out of the grille. That is worth doing
     * once, on the way in, because you are facing the panel when you click it and would otherwise
     * arrive looking into the duct. It is not worth doing on a hop: by then the player has chosen
     * where to look, and having it taken off them mid-transition is disorienting.
     */
    private void sendState(Player player, boolean orient) {
        if (player instanceof ServerPlayer serverPlayer) {
            // The HUD shows coordinates, and a rider wants the ones on their map, not a grid's own.
            List<BlockPos> shown = ensureDestinations().stream()
                    .map(vent -> BlockPos.containing(toWorld(level(), vent, Vec3.atCenterOf(vent))))
                    .toList();
            Optional<Direction> orientTo = orient
                    ? Optional.of(facingOf(level().getBlockState(ventPos)))
                    : Optional.empty();
            PacketDistributor.sendToPlayer(serverPlayer,
                    new DuctTravelStateS2CPacket(shown, selected, orientTo));
        }
    }

    // --- placement --------------------------------------------------------------------------

    private void placeAtVent(BlockState state) {
        Direction facing = facingOf(state);
        entityData.set(VENT_FACING, facing);

        // In the vent's own block space, grid or world. moveTo clears the previous position too,
        // so a hop across the map is a cut rather than a hundred-block interpolated smear.
        Vec3 peek = Vec3.atCenterOf(ventPos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(HEAD_OFFSET));
        moveTo(peek.x, peek.y, peek.z, yawFor(facing), pitchFor(facing));
    }

    /**
     * A point given in the vent's own block space, in the world. The identity for a vent in the
     * world proper; for one on a grid, wherever that grid is showing that point right now.
     */
    private Vec3 toWorld(Vec3 local) {
        return toWorld(level(), ventPos, local);
    }

    private static Vec3 toWorld(Level level, BlockPos anchor, Vec3 local) {
        return Compat.toWorldPos(level, anchor, local);
    }

    /**
     * The entity sits at the centre of the head, so the rider's position — the underside of their
     * box — is one eye height below it.
     */
    @Override
    public @NotNull Vec3 getPassengerRidingPosition(@NotNull Entity passenger) {
        return position().subtract(0.0, passenger.getEyeHeight(), 0.0);
    }

    /** The rider steers nothing — cycling is the only control, and it goes over the network. */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    /**
     * Where a rider is put down. Every candidate is worked out and checked for room in the vent's
     * own block space, where its blocks are, and the one chosen is then taken into the world, which
     * is where the rider has to end up.
     */
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        // The vent is not where this vehicle is: a sublevel taken away has thrown the vehicle out
        // into the world and left the vent's grid position pointing at nothing, or the position
        // never named a vent at all. Its coordinates then say nothing about where the rider is, and
        // the one place known to be right is the vehicle itself, which Sable has already put where
        // it belongs.
        if (Vec3.atCenterOf(ventPos).distanceToSqr(position()) > LOST_VENT_DISTANCE * LOST_VENT_DISTANCE) {
            return new Vec3(getX(), getY() - HEAD_SIZE / 2.0, getZ());
        }

        Direction facing = facingOf(level().getBlockState(ventPos));

        BlockPos outside = ventPos.relative(facing);
        double x = outside.getX() + 0.5;
        double z = outside.getZ() + 0.5;

        // A vent in a floor is a plate lying on the block beneath, with most of its own block open
        // above it. Climbing out of one means standing on the grille, not being lifted a block
        // into the air to fall back onto it.
        if (facing == Direction.UP) {
            Vec3 onPlate = new Vec3(ventPos.getX() + 0.5,
                    ventPos.getY() + VentBlock.PLATE_THICKNESS / 16.0, ventPos.getZ() + 0.5);
            if (DismountHelper.canDismountTo(level(), onPlate, passenger, Pose.STANDING)) {
                return toWorld(onPlate);
            }
        }

        // A vent in a ceiling opens straight down, so the block immediately outside it is the one
        // the player's head would be standing in. Every other facing puts that block beside or
        // above the vent, where a full-height body clears it; here it has to drop a whole block
        // further or they come out wedged in the grille they just left.
        if (facing == Direction.DOWN) {
            Vec3 below = new Vec3(x, outside.getY() - 1.0, z);
            if (DismountHelper.canDismountTo(level(), below, passenger, Pose.STANDING)) {
                return toWorld(below);
            }
        }

        Vec3 inFront = new Vec3(x, outside.getY(), z);
        if (DismountHelper.canDismountTo(level(), inFront, passenger, Pose.STANDING)) {
            return toWorld(inFront);
        }

        // Someone has walled the grille over. Try to stand on top of the vent instead.
        Vec3 above = new Vec3(ventPos.getX() + 0.5, ventPos.getY() + 1.0, ventPos.getZ() + 0.5);
        if (DismountHelper.canDismountTo(level(), above, passenger, Pose.STANDING)) {
            return toWorld(above);
        }

        // Nowhere is clear. Better to leave them in the vent's own space than in limbo.
        return toWorld(new Vec3(ventPos.getX() + 0.5, ventPos.getY(), ventPos.getZ() + 0.5));
    }

    // --- lifecycle --------------------------------------------------------------------------

    @Override
    public void tick() {
        baseTick();

        if (level().isClientSide()) {
            return;
        }
        if (getPassengers().isEmpty()) {
            discard();
            return;
        }

        BlockState state = level().getBlockState(ventPos);
        if (!(state.getBlock() instanceof VentBlock) || VentBlock.isShut(state)
                || Compat.isOrphanedGridPos(level(), ventPos)) {
            // The vent was broken or sealed while the player was peeking out of it — or the ship
            // or sublevel carrying it has gone, leaving its blocks lying in a grid region nothing
            // owns, where a vent may still read as one for a while. Spit them out rather than
            // leaving them parked inside a block that is no longer there.
            //
            // The journey ends here whether or not it had finished: RiderRules refuses to let a
            // traveller off, and it would hold them just as firmly to an entity about to stop
            // existing.
            entityData.set(TRAVELLING, false);
            ejectPassengers();
            discard();
            return;
        }

        entityData.set(VENT_FACING, facingOf(state));

        if (!isTravelling()) {
            exposeToGas();
        }

        if (cooldown > 0) {
            cooldown--;
        }
        // Arrival is checked before departure so a hop set up this tick starts its crawl on the
        // next one rather than losing a tick to this decrement.
        if (arriveDelay > 0 && --arriveDelay == 0) {
            entityData.set(TRAVELLING, false);
            Player arriving = rider();
            if (arriving != null) {
                sendState(arriving, false);
            }
            // The grille opening at the far end, heard as the screen comes back.
            if (level() instanceof ServerLevel server) {
                clank(server, ventPos, state);
            }
        }
        if (hopDelay > 0 && --hopDelay == 0) {
            Player rider = rider();
            if (rider != null) {
                cycle(pendingDelta, rider);
            }
        }

        // Unprompted, while nothing else is going on: a hop refreshes the list itself, and a
        // rider mid-crawl will be told what is there when they arrive.
        if (hopDelay == 0 && arriveDelay == 0 && --refreshDelay <= 0) {
            refreshDelay = REFRESH_INTERVAL_TICKS;
            List<BlockPos> before = destinations;
            // The same list comes back when nothing has changed, so only a change is sent.
            if (refreshDestinations() != before) {
                Player rider = rider();
                if (rider != null) {
                    sendState(rider, false);
                }
            }
        }
    }

    /**
     * A vent with hot or cold gas going through it is no place to put your head: the rider gets
     * {@link GasExposure} to whatever is passing, for as long as they stay and the gas stays as it
     * is — which, since a hop is always available, is their choice.
     *
     * <p>Only while sitting in the vent: between vents the rider is nowhere, and nothing reaches
     * them there.
     */
    private void exposeToGas() {
        if (!(level().getBlockEntity(ventPos) instanceof VentBlockEntity vent)) {
            return;
        }
        double temperature = vent.gasTemperatureK();
        if (!GasExposure.isHarmful(temperature)) {
            return;
        }
        for (Entity passenger : getPassengers()) {
            GasExposure.expose(passenger, temperature);
        }
    }

    /** The player riding this, if there is one. There is never more than one. */
    private @Nullable Player rider() {
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * A hop is a cut, not a journey. Left to itself the client would ease the vehicle across the
     * gap over the next few ticks and drag the rider's camera through everything in between.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        setPos(x, y, z);
        setRot(yRot, xRot);
        setOldPosAndRot();
        snapPassengers();
    }

    /**
     * Cut the riders to wherever this entity now is, instead of letting them slide there.
     *
     * <p>A rider's position never crosses the network — every client derives it from the vehicle
     * each tick — so on a hop their previous position is still back at the vent they left, and the
     * renderer draws the head streaking across the map to the new one. Their rotation does cross
     * the network, and vanilla eases it in over three ticks, which swings the head round on
     * arrival. Neither is wanted here: a hop is a cut, and the head should simply be somewhere
     * else the next frame.
     *
     * <p>Called from both sides — from the hop on the server, and from {@link #lerpTo} on every
     * client that can see the vent, which is what makes it right for onlookers and not just for
     * whoever is inside.
     */
    private void snapPassengers() {
        for (Entity passenger : getPassengers()) {
            // Through the plain positionRider, which is the one Sable hooks to carry a rider out of
            // a grid's space into the world; only then is the previous position taken along too.
            positionRider(passenger);
            passenger.setOldPosAndRot();

            if (passenger instanceof LivingEntity living) {
                living.yHeadRotO = living.yHeadRot;
                living.yBodyRotO = living.yBodyRot;
                // Zero steps cancels an easing already in flight without starting another.
                living.lerpTo(living.getX(), living.getY(), living.getZ(),
                        living.getYRot(), living.getXRot(), 0);
                living.lerpHeadTo(living.yHeadRot, 0);
            }
        }
    }

    @Override
    protected void addPassenger(@NotNull Entity passenger) {
        super.addPassenger(passenger);
        // Shrink them to a head. Both sides run this, so the box the server hit-tests against and
        // the one the client draws around agree.
        passenger.refreshDimensions();
    }

    @Override
    protected void removePassenger(@NotNull Entity passenger) {
        super.removePassenger(passenger);
        // The vehicle is already cleared by now, so this puts them back to their own size.
        passenger.refreshDimensions();

        if (level() instanceof ServerLevel server) {
            clank(server, ventPos, server.getBlockState(ventPos));
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        // The destination list is rebuilt on demand; only where we are needs to survive a reload.
        ventPos = NbtUtils.readBlockPos(tag, VENT_TAG).orElse(BlockPos.ZERO);
        destinations = null;
        selected = 0;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.put(VENT_TAG, NbtUtils.writeBlockPos(ventPos));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(VENT_FACING, Direction.UP);
        builder.define(TRAVELLING, false);
    }

    /** Which way the vent the rider is looking out of opens. Valid on both sides. */
    public Direction getVentFacing() {
        return entityData.get(VENT_FACING);
    }

    /**
     * Whether the rider is somewhere in the run between two vents. Valid on both sides.
     *
     * <p>Nothing of them exists to the world while this holds: they are not drawn, cannot be hurt,
     * and cannot touch anything. They are in the ducts, and the ducts are not a place.
     */
    public boolean isTravelling() {
        return entityData.get(TRAVELLING);
    }

    /** Whether this entity is riding a duct and currently between vents. */
    public static boolean isInTransit(Entity entity) {
        return entity.getVehicle() instanceof DuctTravelEntity duct && duct.isTravelling();
    }

    /**
     * Whether nothing of this entity should be drawn — not its shadow, not its debug hitbox —
     * because it is a rider between vents, or the duct carrying one. Either would give away
     * where the rider is about to appear.
     */
    public static boolean isHiddenInTransit(Entity entity) {
        return isInTransit(entity)
                || (entity instanceof DuctTravelEntity duct && duct.isTravelling());
    }

    // --- helpers ----------------------------------------------------------------------------

    public BlockPos getVentPos() {
        return ventPos;
    }

    /** The vents currently on offer, as last worked out. Server side only. */
    public List<BlockPos> getDestinations() {
        return ensureDestinations();
    }

    /**
     * Which way the vent opens. Falls back to up for a block that is no longer a vent, which only
     * happens on the tick between it being broken and the rider being ejected.
     */
    private static Direction facingOf(BlockState state) {
        return state.hasProperty(VentBlock.FACING) ? state.getValue(VentBlock.FACING) : Direction.UP;
    }

    public static float yawFor(Direction facing) {
        return facing.getAxis().isHorizontal() ? facing.toYRot() : 0.0f;
    }

    public static float pitchFor(Direction facing) {
        return switch (facing) {
            case UP -> -90.0f;
            case DOWN -> 90.0f;
            default -> 0.0f;
        };
    }

    /**
     * Applies {@link #RIDER_DIMENSIONS} to whoever is inside a duct.
     *
     * <p>This is the only hook that reaches a player's own size, and it fires on both sides, so a
     * rider is a head-sized target to attack, to shoot, and to bump into — not just to look at.
     * It runs off {@code refreshDimensions}, which is why mounting and dismounting call it.
     */
    @EventBusSubscriber(modid = ZPSMod.MOD_ID)
    public static final class RiderRules {

        private RiderRules() {
        }

        @SubscribeEvent
        public static void onEntitySize(EntityEvent.Size event) {
            if (event.getEntity().getVehicle() instanceof DuctTravelEntity) {
                event.setNewSize(RIDER_DIMENSIONS);
            }
        }

        /**
         * Nothing reaches someone mid-crawl. This is the first event in the damage sequence, so
         * cancelling it takes the knockback and the hurt animation with the damage.
         */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (isInTransit(event.getEntity())) {
                event.setCanceled(true);
            }
        }

        /**
         * No climbing out halfway along. Sneaking is how a rider leaves a vent, and vanilla honours
         * it on any tick at all — including the ones spent in the run between two, where taking it
         * would drop the player at the far end without waiting out the journey and sidestep the
         * only thing rationing duct travel.
         *
         * <p>Server side alone. The dismount this refuses only ever happens there, while the
         * client dismounts on being told to by the server — refusing that would leave the two
         * disagreeing about whether anyone is still in the duct.
         */
        @SubscribeEvent
        public static void onDismount(EntityMountEvent event) {
            if (event.isDismounting()
                    && !event.getLevel().isClientSide()
                    && event.getEntityBeingMounted() instanceof DuctTravelEntity duct
                    && duct.isTravelling()) {
                event.setCanceled(true);
            }
        }

        // ...and they reach nothing. The screen is black and the camera is somewhere inside a
        // wall, so every one of these would otherwise be a blind click on whatever happens to be
        // in front of a vent they have not arrived at yet.

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            deny(event, event.getEntity());
        }

        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            deny(event, event.getEntity());
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            deny(event, event.getEntity());
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            deny(event, event.getEntity());
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            deny(event, event.getEntity());
        }

        @SubscribeEvent
        public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
            deny(event, event.getEntity());
        }

        /** Dropping something counts: it would leave an item lying inside the duct run. */
        @SubscribeEvent
        public static void onItemToss(ItemTossEvent event) {
            deny(event, event.getPlayer());
        }

        private static void deny(ICancellableEvent event, Entity actor) {
            if (isInTransit(actor)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * The tell for everyone else: a metallic clatter and a puff out of the grille. A head in a
     * grille is easy to miss, but a vent going off next to you means someone just came or went.
     *
     * <p>One per vent per journey: the one being left goes off as the player sets out, the one
     * ahead as they arrive. On a long crawl those are seconds apart, which is the only thing an
     * onlooker gets to hear of someone moving through the walls.
     */
    private static void clank(ServerLevel level, BlockPos pos, BlockState state) {
        clank(level, pos, state, null);
    }

    /** As above, but {@code except} is left out — they are being given the sound some other way. */
    private static void clank(ServerLevel level, BlockPos pos, BlockState state,
                              @Nullable Player except) {
        Direction facing = facingOf(state);
        Vec3 mouth = toWorld(level, pos, Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.55)));
        level.playSound(except, mouth.x, mouth.y, mouth.z, CLANK_SOUND, SoundSource.BLOCKS,
                CLANK_VOLUME, CLANK_PITCH);
        level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 8, 0.15, 0.15, 0.15, 0.02);
    }
}
