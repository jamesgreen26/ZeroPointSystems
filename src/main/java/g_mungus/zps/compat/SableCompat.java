package g_mungus.zps.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.networking.RoomGridS2CPacket;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SableCompat {

    /** The sublevel each player's client was last told owns the room the player is in; empty for none. */
    private static final Map<ServerPlayer, Optional<UUID>> SENT_ROOM_SUBLEVEL = new WeakHashMap<>();

    /** Client side: the sublevel owning the room the local player is in, as the server last said. */
    private static volatile @Nullable UUID localPlayerRoomSubLevel;

    private SableCompat() {
    }

    /// Only call after verifying that Sable is loaded.
    /// Has the sublevel that owns the room around entity carry it, when no sublevel carries it already.
    ///
    /// Sable carries what stands on a sublevel or has its blocks close by, and lets go of anything
    /// adrift in the middle of a cabin, which the ship then leaves behind. It lets go inside
    /// Entity#move, so this is called at the end of every move to take hold again: the next move
    /// carries the entity through that tick's change of pose, and the position sent to clients in
    /// between is the one in the sublevel.
    ///
    /// A player's movement is its own client's, and so is its carrying: the server tells that client
    /// which sublevel owns the player's room (#syncRoomSubLevel), and leaves its own copy of the
    /// player to follow what the client reports.
    static void carryWithRoom(Entity entity) {
        if (Sable.HELPER.getTrackingSubLevel(entity) != null || entity.isPassenger()) {
            return;
        }
        SubLevel owner;
        if (entity.level() instanceof ServerLevel level) {
            if (entity instanceof ServerPlayer) {
                return;
            }
            owner = roomSubLevelAround(level, entity);
        } else if (entity instanceof Player player && player.isLocalPlayer()) {
            UUID id = localPlayerRoomSubLevel;
            SubLevelContainer container = id == null ? null : SubLevelContainer.getContainer(entity.level());
            owner = container == null ? null : container.getSubLevel(id);
        } else {
            // Everything else on a client goes where the server puts it.
            return;
        }
        if (owner != null && !owner.isRemoved()) {
            ((EntityMovementExtension) entity).sable$setTrackingSubLevel(owner);
        }
    }

    /// Only call after verifying that Sable is loaded.
    /// Tells player's client which sublevel owns the room the player is in, whenever that changes.
    static void syncRoomSubLevel(ServerPlayer player) {
        if (player instanceof FakePlayer || !player.connection.hasChannel(RoomGridS2CPacket.TYPE)) {
            return;
        }
        SubLevel owner = roomSubLevelAround(player.serverLevel(), player);
        Optional<UUID> id = Optional.ofNullable(owner).map(SubLevel::getUniqueId);
        // A player the client has heard nothing about yet is told even of no room: it may remember an old one.
        if (!id.equals(SENT_ROOM_SUBLEVEL.put(player, id))) {
            PacketDistributor.sendToPlayer(player, new RoomGridS2CPacket(id));
        }
    }

    static void setLocalPlayerRoomSubLevel(@Nullable UUID id) {
        localPlayerRoomSubLevel = id;
    }

    /// The sublevel whose plot holds a room, active or dormant, around entity's eyes, or null. An
    /// entity kept in a plot itself is Sable's to move, not a sublevel's to carry.
    private static @Nullable SubLevel roomSubLevelAround(ServerLevel level, Entity entity) {
        RoomManager rooms = RoomManager.get(level);
        if (rooms.roomCount() == 0 || Sable.HELPER.getContaining(entity) != null) {
            return null;
        }
        Vec3 eyes = entity.getEyePosition();
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(entity.getBoundingBox()))) {
            if (rooms.knownRoomAt(BlockPos.containing(subLevel.logicalPose().transformPositionInverse(eyes))) != null) {
                return subLevel;
            }
        }
        return null;
    }

    static void registerScriptTypes(RegisterScriptTypesEvent event) {
        ShipScripts.registerScriptTypes(event);
        event.registerApplicability(OnSubLevel.TYPE);
    }

    /** For targets on a sublevel, such as the {@code ship} getter, which reads nothing elsewhere. */
    enum OnSubLevel implements TargetApplicability {
        INSTANCE;

        static final Type<OnSubLevel> TYPE = new Type<>(ZPSMod.resource("on_sublevel"), StreamCodec.unit(INSTANCE));

        @Override
        public boolean appliesTo(Level level, ScriptTarget target) {
            return isOnSubLevel(level, target.pos());
        }

        @Override
        public Type<?> type() {
            return TYPE;
        }
    }

    static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        ShipScripts.registerScriptCommands(event, context -> {
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(context.level(), context.pos());
            return subLevel == null ? null : new SubLevelView(subLevel);
        }, OnSubLevel.INSTANCE);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#shipToWorld(ServerLevel, BlockPos).
    static Vec3 subLevelToWorld(ServerLevel level, BlockPos pos) {
        Position center = pos.getCenter();
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, center);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#shipToWorld(Level, Vec3).
    static Vec3 subLevelToWorld(Level level, Vec3 pos) {
        Position position = pos;
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
    }

    /// Only call after verifying that Sable is loaded.
    /// Transforms pos (in the local space of the sublevel managing anchorPos) into world space.
    /// anchorPos resolves which sublevel to use, so positions outside the sublevel's strict
    /// bounds still transform correctly. Returns pos unchanged if anchorPos is not inside a
    /// sublevel.
    static Vec3 subLevelToWorld(Level level, BlockPos anchorPos, Vec3 pos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, anchorPos.getCenter());
        if (subLevel == null) return pos;
        return subLevel.logicalPose().transformPosition(pos);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#worldToShip: transforms a world-space position into the
    /// local space of the sublevel managing anchorPos. Returns worldPos unchanged if anchorPos
    /// is not inside a sublevel.
    static Vec3 worldToSubLevel(Level level, BlockPos anchorPos, Vec3 worldPos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, anchorPos.getCenter());
        if (subLevel == null) return worldPos;
        return subLevel.logicalPose().transformPositionInverse(worldPos);
    }

    /// Only call after verifying that Sable is loaded.
    /// True when pos is in the plot grid but no sublevel holds that plot: one has been removed,
    /// and Sable has not yet cleared what it left behind.
    static boolean isOrphanedPlotPos(Level level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null
                && container.inBounds(pos)
                && SableCompanion.INSTANCE.getContaining(level, pos) == null;
    }

    /// Only call after verifying that Sable is loaded.
    static boolean isOnSubLevel(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos) != null;
    }

    /// Only call after verifying that Sable is loaded.
    static @Nullable GridSpace gridOf(Level level, BlockPos pos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        return subLevel == null ? null : new SubLevelSpace(level, subLevel);
    }

    /// Only call after verifying that Sable is loaded.
    static List<GridSpace> gridsTouching(Level level, AABB worldBounds) {
        List<GridSpace> grids = new ArrayList<>();
        // Sable forbids touching the bounds while iterating; they are not kept past this call.
        for (SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(worldBounds))) {
            grids.add(new SubLevelSpace(level, subLevel));
        }
        return grids;
    }

    /// Only call after verifying that Sable is loaded.
    static boolean assemble(ServerLevel level, BoundingBox volume) {
        // A plot holds chunks only where blocks have been put, and the rest of it is empty rather than unloaded.
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        boolean inPlot = container != null && container.inBounds(new BlockPos(volume.minX(), volume.minY(), volume.minZ()));
        if (!inPlot && !level.hasChunksAt(volume.minX(), volume.minZ(), volume.maxX(), volume.maxZ())) return false;

        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(volume.minX(), volume.minY(), volume.minZ(),
                volume.maxX(), volume.maxY(), volume.maxZ())) {
            if (!level.getBlockState(pos).isAir()) blocks.add(pos.immutable());
        }
        if (blocks.isEmpty()) return false;

        // One block of room all round, as Sable's own area assembly leaves.
        BoundingBox3i bounds = new BoundingBox3i(volume.minX() - 1, volume.minY() - 1, volume.minZ() - 1,
                volume.maxX() + 1, volume.maxY() + 1, volume.maxZ() + 1);
        SubLevelAssemblyHelper.assembleBlocks(level, blocks.getFirst(), blocks, bounds);
        return true;
    }

    private record SubLevelSpace(Level level, SubLevelAccess subLevel) implements GridSpace {
        @Override
        public Vec3 toLocal(Vec3 world) {
            return subLevel.logicalPose().transformPositionInverse(world);
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return subLevel.logicalPose().transformPosition(local);
        }

        /// Sable asks for the point in the sublevel's own space and answers per second.
        @Override
        public Vec3 velocityAt(Vec3 world) {
            Vec3 local = toLocal(world);
            Vector3d velocity = SableCompanion.INSTANCE.getVelocity(level, subLevel,
                    new Vector3d(local.x, local.y, local.z), new Vector3d());
            return new Vec3(velocity.x / 20.0, velocity.y / 20.0, velocity.z / 20.0);
        }

        @Override
        public boolean isSameGrid(GridSpace other) {
            return other instanceof SubLevelSpace(Level otherLevel, SubLevelAccess otherSubLevel)
                    && otherSubLevel.getUniqueId().equals(subLevel.getUniqueId());
        }
    }

    /**
     * A sublevel as the server tick sees it. On the server Sable's are {@link ServerSubLevel}s, which also know
     * their motion and mass; one that is not reads as standing still and weighing nothing.
     */
    private record SubLevelView(SubLevelAccess subLevel) implements ShipScripts.ShipView {
        @Override
        public String name() {
            return subLevel.getName() == null ? "" : subLevel.getName();
        }

        @Override
        public String id() {
            return subLevel.getUniqueId().toString();
        }

        /** The plot's extent, in blocks; {@code SubLevelAccess#boundingBox} is the world AABB, which grows as it turns. */
        @Override
        public Vec3 boundingBox() {
            if (!(subLevel instanceof SubLevel fullSubLevel)) {
                return Vec3.ZERO;
            }
            BoundingBox3ic bounds = fullSubLevel.getPlot().getBoundingBox();
            if (bounds.maxX() < bounds.minX() || bounds.maxY() < bounds.minY() || bounds.maxZ() < bounds.minZ()) {
                return Vec3.ZERO;
            }
            // Plot bounds are inclusive block coordinates.
            return new Vec3(
                    bounds.maxX() - bounds.minX() + 1,
                    bounds.maxY() - bounds.minY() + 1,
                    bounds.maxZ() - bounds.minZ() + 1
            );
        }

        @Override
        public Vec3 position() {
            return toMinecraft(subLevel.logicalPose().position());
        }

        @Override
        public Vec3 worldVelocity() {
            return subLevel instanceof ServerSubLevel server ? toMinecraft(server.latestLinearVelocity) : Vec3.ZERO;
        }

        @Override
        public Vec3 angularVelocity() {
            return subLevel instanceof ServerSubLevel server ? toMinecraft(server.latestAngularVelocity) : Vec3.ZERO;
        }

        @Override
        public Vec3 centreOfMass() {
            return subLevel instanceof ServerSubLevel server
                    ? toMinecraft(server.getMassTracker().getCenterOfMass())
                    : position();
        }

        @Override
        public double mass() {
            return subLevel instanceof ServerSubLevel server ? server.getMassTracker().getMass() : 0;
        }

        @Override
        public Vec3 toWorldDirection(Vec3 direction) {
            return subLevel.logicalPose().transformNormal(direction);
        }

        @Override
        public Vec3 toGridDirection(Vec3 direction) {
            return subLevel.logicalPose().transformNormalInverse(direction);
        }
    }

    private static Vec3 toMinecraft(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
