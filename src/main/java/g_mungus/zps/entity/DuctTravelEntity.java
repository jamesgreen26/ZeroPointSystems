package g_mungus.zps.entity;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.block.gas.core.DuctTravelNetwork;
import g_mungus.zps.blockentity.gas.VentBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.GasExposure;
import g_mungus.zps.networking.DuctCycleRefusedS2CPacket;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
     * Where the grille is, measured from the vent's block centre along the way it faces. The plate
     * is pressed back against the duct feeding it, so its face sits well inside the vent's own
     * block: a negative offset, behind the centre. Everything that meets the vent — the rider's
     * head, a player climbing out, the puff and clatter of someone coming or going — is placed
     * against this face, not against the block.
     */
    public static final double GRILLE_OFFSET = VentBlock.PLATE_THICKNESS / 16.0 - 0.5;

    /**
     * How far out of the vent's block centre the head sits: to the face of the grille, then half a
     * head to clear it. The result is a head cube resting flush against the outside of the grille,
     * centred on it, which is what anyone walking past sees.
     */
    private static final double HEAD_OFFSET = GRILLE_OFFSET + HEAD_SIZE / 2.0;

    /** How far off the grille the puff and clatter of a vent are placed, so they are not inside it. */
    private static final double MOUTH_CLEARANCE = 0.05;

    /**
     * What the rider is while they are in here. The eye sits at the middle of the cube, so the
     * camera, the hitbox and the head model all centre on the same point.
     */
    public static final EntityDimensions RIDER_DIMENSIONS = EntityDimensions.fixed(HEAD_SIZE, HEAD_SIZE);
    /** The eye sits at the middle of the head cube; set alongside the size, which cannot carry it in this version. */
    public static final float RIDER_EYE_HEIGHT = HEAD_SIZE / 2.0f;

    private static final String VENT_TAG = "Vent";

    /**
     * Which way the vent currently being looked out of opens. Synched because everyone rendering
     * the rider needs it, not just the rider: a head hanging out of a ceiling is drawn upside down,
     * and onlookers are the ones who see that.
     */
    private static final EntityDataAccessor<Direction> VENT_FACING =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.DIRECTION);

    /**
     * The vent currently being looked out of, in the level's block space. Synched for the rider's
     * own client: when the vehicle is on a Sable sublevel, Sable has that client work out where to
     * put the player down from its own copy of the vehicle, and then takes the position the client
     * reports — the sublevel's blocks are not in the world for it to see the player has been put
     * inside one. Left unsynched, the copy knows no vent, falls back on the vehicle's own spot,
     * and the player is stood up out of the grille on top of it.
     */
    private static final EntityDataAccessor<BlockPos> VENT_POS =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.BLOCK_POS);

    /**
     * Whether the rider is between vents rather than sitting in one. Synched for the same reason
     * the facing is: it decides whether anyone draws them at all.
     */
    private static final EntityDataAccessor<Boolean> TRAVELLING =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * When the current crawl began, in game time, and how many ticks it takes. Synched so the
     * rider's client can draw how far along it is: game time is kept in step with the server, so
     * the two together give the progress without a packet a tick.
     */
    private static final EntityDataAccessor<Long> CRAWL_START =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CRAWL_TICKS =
            SynchedEntityData.defineId(DuctTravelEntity.class, EntityDataSerializers.INT);

    /**
     * Ticks between asking for another vent and actually being moved there. The client spends them
     * fading to black, so the move lands unseen; the server counts from when the request arrived,
     * which is at or after the client began fading, so it is always covered.
     */
    public static final int FADE_OUT_TICKS = 6;
    /** Ticks the client spends fading back in once the move has landed. */
    public static final int FADE_IN_TICKS = 8;

    /**
     * How far into the fade back in the client may ask for the next vent. Waiting out the whole
     * fade-in between hops was found to drag when stepping through a run of vents; a couple of
     * ticks is enough for the far vent to register before the screen is on its way dark again.
     */
    public static final int REQUEST_AGAIN_TICKS = 2;

    /**
     * Ticks after a hop lands, or is turned down, before another request is taken. This is the rate
     * limit on duct travel, held here rather than trusted to the client.
     *
     * <p>One tick shorter than the client's own wait on purpose. The client asks no sooner than
     * {@link #REQUEST_AGAIN_TICKS} after the arrival reaches it, and that request is handled here
     * between two server ticks, against a clock that is not lined up with the client's. Were the
     * two the same length, a request sent on the first tick the client allows would land, about
     * half the time, with one tick still on the cooldown and be turned down. A refusal is answered
     * in any case, so a client that gets ahead under lag is still brought back.
     */
    public static final int ARRIVAL_COOLDOWN_TICKS = REQUEST_AGAIN_TICKS - 1;

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

    /** The vent currently being looked out of. Server side; the client reads {@link #VENT_POS}. */
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
     * behind a black screen, and nothing further is accepted until
     * {@link #ARRIVAL_COOLDOWN_TICKS} after it lands.
     *
     * <p>Returns false when the request is turned down: a hop is already under way, or the last one
     * has only just landed. The rider is told so, because their screen began going dark the moment
     * they asked and would otherwise stay that way waiting for an arrival that is never coming.
     * A request is never dropped without an answer.
     */
    public boolean requestCycle(int delta) {
        if (hopDelay > 0 || arriveDelay > 0 || cooldown > 0) {
            Player rider = rider();
            if (rider instanceof ServerPlayer serverPlayer) {
                ZPSGamePackets.sendToPlayer(serverPlayer, new DuctCycleRefusedS2CPacket());
            }
            return false;
        }
        pendingDelta = delta;
        hopDelay = FADE_OUT_TICKS;
        cooldown = FADE_OUT_TICKS + ARRIVAL_COOLDOWN_TICKS;
        return true;
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
        cooldown = arriveDelay + ARRIVAL_COOLDOWN_TICKS;
        entityData.set(CRAWL_START, level().getGameTime());
        entityData.set(CRAWL_TICKS, arriveDelay);
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
            ZPSGamePackets.sendToPlayer(serverPlayer,
                    new DuctTravelStateS2CPacket(shown, selected, orientTo));
        }
    }

    // --- placement --------------------------------------------------------------------------

    private void placeAtVent(BlockState state) {
        Direction facing = facingOf(state);
        entityData.set(VENT_FACING, facing);
        entityData.set(VENT_POS, ventPos);

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
        return toWorld(level(), getVentPos(), local);
    }

    private static Vec3 toWorld(Level level, BlockPos anchor, Vec3 local) {
        return Compat.toWorldPos(level, anchor, local);
    }

    /**
     * The entity sits at the centre of the head, so the rider's position — the underside of their
     * box — is one eye height below it.
     */
    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        if (hasPassenger(passenger)) {
            Vec3 spot = position().subtract(0.0, passenger.getEyeHeight(), 0.0);
            callback.accept(passenger, spot.x, spot.y, spot.z);
        }
    }

    /**
     * The same spot as {@link #positionRider}, in the vanilla form. Valkyrien Skies ignores
     * {@code positionRider} for anyone mounted on a ship and puts them at the vehicle plus this
     * plus their own riding offset instead ({@code VSGameUtils.getShipMountedToData}); left at the
     * default {@code 0.75 * height}, a player would sit 0.275 above where the head belongs.
     */
    @Override
    public double getPassengersRidingOffset() {
        Entity passenger = getFirstPassenger();
        if (passenger == null) {
            return super.getPassengersRidingOffset();
        }
        return -passenger.getEyeHeight() - passenger.getMyRidingOffset();
    }

    /** The rider steers nothing — cycling is the only control, and it goes over the network. */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    /**
     * Where a rider is put down: right against the grille they were looking out of, the way they
     * would be if they had stepped out of it. Out of a wall vent they stand in the vent's own block
     * with their back to the plate; out of a floor vent they stand on the plate; out of a ceiling
     * vent they hang from it, with the top of their head just under the grille. There is no
     * searching for room — a vent is a way through a wall, and whatever is on the other side is
     * where the rider ends up, exactly as in the world proper.
     *
     * <p>Worked out in the vent's own block space, where its blocks are, and then taken into the
     * world, which is where the rider has to end up.
     *
     * <p>Runs on the rider's client as well as the server when the vehicle is on a sublevel — Sable
     * asks the client's copy, and the server takes its word — so everything here comes from what is
     * synched: the vent's position and facing, and the block at it.
     */
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        BlockPos ventPos = getVentPos();

        // The vent is not where this vehicle is: a sublevel taken away has thrown the vehicle out
        // into the world and left the vent's grid position pointing at nothing, or the position
        // never named a vent at all. Its coordinates then say nothing about where the rider is, and
        // the one place known to be right is the vehicle itself, which Sable has already put where
        // it belongs.
        if (Vec3.atCenterOf(ventPos).distanceToSqr(position()) > LOST_VENT_DISTANCE * LOST_VENT_DISTANCE) {
            return new Vec3(getX(), getY() - HEAD_SIZE / 2.0, getZ());
        }

        // From the block rather than the synched facing: a vent that has just been broken reads as
        // facing up, which puts a rider being thrown out down where it stood rather than beside it.
        Direction facing = facingOf(level().getBlockState(ventPos));
        Vec3 grille = Vec3.atCenterOf(ventPos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(GRILLE_OFFSET));
        // Their size on their feet, not the head they are while they ride.
        EntityDimensions standing = passenger.getDimensions(Pose.STANDING);

        Vec3 spot = switch (facing) {
            // Standing on the plate.
            case UP -> grille;
            // Hanging under it: the top of the head level with the grille.
            case DOWN -> grille.subtract(0.0, standing.height, 0.0);
            // Feet on the vent block's floor, body pressed to the grille.
            default -> new Vec3(grille.x, ventPos.getY(), grille.z)
                    .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(standing.width / 2.0));
        };
        return toWorld(spot);
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
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
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
                        living.getYRot(), living.getXRot(), 0, false);
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
        ventPos = tag.contains(VENT_TAG, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(VENT_TAG)) : BlockPos.ZERO;
        entityData.set(VENT_POS, ventPos);
        destinations = null;
        selected = 0;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.put(VENT_TAG, NbtUtils.writeBlockPos(ventPos));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VENT_FACING, Direction.UP);
        entityData.define(VENT_POS, BlockPos.ZERO);
        entityData.define(TRAVELLING, false);
        entityData.define(CRAWL_START, 0L);
        entityData.define(CRAWL_TICKS, 0);
    }

    /**
     * How far along the current crawl is, 0 to 1, or 0 when the rider is sitting in a vent. Valid
     * on both sides; the client draws its progress bar from it.
     */
    public float crawlProgress(float partialTick) {
        if (!isTravelling()) {
            return 0.0f;
        }
        int ticks = entityData.get(CRAWL_TICKS);
        if (ticks <= 0) {
            return 1.0f;
        }
        double elapsed = level().getGameTime() - entityData.get(CRAWL_START) + partialTick;
        return Mth.clamp((float) (elapsed / ticks), 0.0f, 1.0f);
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

    /**
     * The vent the rider is looking out of, in the level's block space. Valid on both sides: read
     * from the synched copy, which the server keeps equal to its own, so that a copy of this entity
     * built from nothing but its synched data — a client's — answers the same as the original.
     */
    public BlockPos getVentPos() {
        return entityData.get(VENT_POS);
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
    @Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
    public static final class RiderRules {

        private RiderRules() {
        }

        @SubscribeEvent
        public static void onEntitySize(EntityEvent.Size event) {
            if (event.getEntity().getVehicle() instanceof DuctTravelEntity) {
                event.setNewSize(RIDER_DIMENSIONS);
                event.setNewEyeHeight(RIDER_EYE_HEIGHT);
            }
        }

        /**
         * Nothing reaches someone mid-crawl. This is the first event in the damage sequence, so
         * cancelling it takes the knockback and the hurt animation with the damage.
         */
        @SubscribeEvent
        public static void onIncomingDamage(LivingAttackEvent event) {
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

        private static void deny(Event event, Entity actor) {
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
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(GRILLE_OFFSET + MOUTH_CLEARANCE)));
        level.playSound(except, mouth.x, mouth.y, mouth.z, CLANK_SOUND, SoundSource.BLOCKS,
                CLANK_VOLUME, CLANK_PITCH);
        level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 8, 0.15, 0.15, 0.15, 0.02);
    }
}
