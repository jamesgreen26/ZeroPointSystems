package g_mungus.zps.compat;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import net.neoforged.neoforge.server.command.EnumArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.bodies.properties.BodyKinematics;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ChunkClaim;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.ArrayList;
import java.util.List;

public class VSCompat {

    /// Only call after verifying that VS is loaded
    static Vec3 shipToWorld(ServerLevel level, BlockPos pos) {
        return VSGameUtilsKt.toWorldCoordinates(level, pos);
    }

    /// Only call after verifying that VS is loaded
    static Vec3 shipToWorld(Level level, Vec3 pos) {
        return VSGameUtilsKt.toWorldCoordinates(level, pos);
    }

    /// Only call after verifying that VS is loaded.
    /// True when pos is in the shipyard but no ship manages it.
    static boolean isOrphanedShipyardPos(Level level, BlockPos pos) {
        return VSGameUtilsKt.isBlockInShipyard(level, pos)
                && VSGameUtilsKt.getShipManagingPos(level, pos) == null;
    }

    /// Only call after verifying that VS is loaded.
    /// Transforms pos (in the ship-space of the ship managing anchorPos) into world coordinates.
    /// anchorPos resolves the ship, so positions outside the ship's strict bounds still transform
    /// correctly. Returns pos unchanged if anchorPos is not on a ship.
    static Vec3 shipToWorld(Level level, BlockPos anchorPos, Vec3 pos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, anchorPos);
        if (ship == null) return pos;
        Vector3d world = ship.getTransform().getShipToWorld()
                .transformPosition(VectorConversionsMCKt.toJOML(pos));
        return VectorConversionsMCKt.toMinecraft(world);
    }

    /// Only call after verifying that VS is loaded.
    /// Transforms a world-space position into the local space of the ship managing anchorPos.
    /// Returns worldPos unchanged if anchorPos is not on a ship.
    static Vec3 worldToShip(Level level, BlockPos anchorPos, Vec3 worldPos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, anchorPos);
        if (ship == null) return worldPos;
        Vector3d local = ship.getTransform().getWorldToShip()
                .transformPosition(VectorConversionsMCKt.toJOML(worldPos));
        return VectorConversionsMCKt.toMinecraft(local);
    }

    /// Only call after verifying that VS is loaded.
    static @Nullable GridSpace gridOf(Level level, BlockPos pos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        return ship == null ? null : new ShipSpace(ship);
    }

    /// Only call after verifying that VS is loaded.
    /// VS's own "which ships are near here" query hands back the point as each nearby ship sees
    /// it rather than the ships, so the ships are looked up again from those points.
    static List<GridSpace> gridsTouching(Level level, AABB worldBounds) {
        Vec3 centre = worldBounds.getCenter();
        double radius = 0.5 * Math.max(worldBounds.getXsize(), Math.max(worldBounds.getYsize(), worldBounds.getZsize()));
        List<GridSpace> grids = new ArrayList<>();
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, centre.x, centre.y, centre.z, radius,
                (DoubleTernaryConsumer) (x, y, z) -> {
                    Ship ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
                    if (ship == null || !intersects(ship.getWorldAABB(), worldBounds)) {
                        return;
                    }
                    ShipSpace space = new ShipSpace(ship);
                    if (grids.stream().noneMatch(space::isSameGrid)) {
                        grids.add(space);
                    }
                });
        return grids;
    }

    private static boolean intersects(AABBdc ship, AABB bounds) {
        return ship.minX() <= bounds.maxX && ship.maxX() >= bounds.minX
                && ship.minY() <= bounds.maxY && ship.maxY() >= bounds.minY
                && ship.minZ() <= bounds.maxZ && ship.maxZ() >= bounds.minZ;
    }

    private record ShipSpace(Ship ship) implements GridSpace {
        @Override
        public Vec3 toLocal(Vec3 world) {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform().getWorldToShip()
                    .transformPosition(VectorConversionsMCKt.toJOML(world)));
        }

        @Override
        public Vec3 toWorld(Vec3 local) {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform().getShipToWorld()
                    .transformPosition(VectorConversionsMCKt.toJOML(local)));
        }

        /// Linear plus angular, both of which VS gives per second.
        @Override
        public Vec3 velocityAt(Vec3 world) {
            Vector3d arm = VectorConversionsMCKt.toJOML(world).sub(ship.getTransform().getPositionInWorld());
            Vector3d velocity = new Vector3d(ship.getAngularVelocity()).cross(arm).add(ship.getVelocity());
            return VectorConversionsMCKt.toMinecraft(velocity.div(20.0));
        }

        @Override
        public boolean isSameGrid(GridSpace other) {
            return other instanceof ShipSpace(Ship otherShip) && otherShip.getId() == ship.getId();
        }
    }

    /** A ship, or {@link #NO_SHIP} where there is none. Reads as its slug where text is wanted. */
    static final ScriptType<Ship> SHIP = ScriptType.opaque(ZPSScriptTypes.key("ship"), Ship.class)
            .usableAs(BuiltInTypes.STRING, ship -> ship == VSCompat.NO_SHIP ? "" : ship.getSlug());

    static void registerScriptTypes(RegisterScriptTypesEvent event) {
        event.register(SHIP);
    }

    static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        event.register(ZPSNodes.getter("ship", SHIP, context -> {
            Ship ship = VSGameUtilsKt.getShipManagingPos(context.level(), context.pos());
            return ship == null ? NO_SHIP : ship;
        }));

        event.register(ZPSNodes.mapper("slug", SHIP, BuiltInTypes.STRING,
                (ship, context) -> ship == NO_SHIP ? "" : ship.getSlug()));

        event.register(ZPSNodes.mapper("id", SHIP, BuiltInTypes.INT,
                (ship, context) -> ship == NO_SHIP ? -1 : (int) ship.getId()));

        event.register(ZPSNodes.mapper("pos", SHIP, ZPSScriptTypes.VEC_POS, (ship, context) -> ship == NO_SHIP
                ? context.pos().getCenter()
                : VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInWorld())));

        event.register(ZPSNodes.mapper("world_vel", SHIP, ZPSScriptTypes.VEC_DIR, (ship, context) -> ship == NO_SHIP
                ? new Vec3(0, 0, 0)
                : VectorConversionsMCKt.toMinecraft(ship.getVelocity())));

        // Ship-space velocity
        event.register(ZPSNodes.mapper("local_vel", SHIP, ZPSScriptTypes.VEC_DIR, (ship, context) -> {
            if (ship == NO_SHIP) {
                return new Vec3(0, 0, 0);
            }
            var vel = ship.getVelocity();
            var local = new Vector3d(vel.x(), vel.y(), vel.z());
            ship.getTransform().getWorldToShip().transformDirection(local);
            return new Vec3(local.x, local.y, local.z);
        }));

        event.register(ZPSNodes.mapper("bounding_box", SHIP, ZPSScriptTypes.VEC_BOX, (ship, context) -> {
            if (ship == NO_SHIP) {
                return new Vec3(0, 0, 0);
            }
            AABBic aabb = ship.getShipAABB();
            assert aabb != null;
            return new Vec3(
                    aabb.maxX() - aabb.minX(),
                    aabb.maxY() - aabb.minY(),
                    aabb.maxZ() - aabb.minZ()
            ).scale(ship.getTransform().getShipToWorldScaling().x());
        }));

        // A face of the ship, as a direction in the world
        event.register(ZPSNodes.rawArgumentMapper("dir", SHIP, ZPSScriptTypes.VEC_DIR, "direction",
                EnumArgument.enumArgument(Direction.class), Direction.class,
                (ship, direction, context) -> shipDirection(ship, direction)));

        // Mass, scaled by the ship's scaled volume
        event.register(ZPSNodes.mapper("mass", SHIP, BuiltInTypes.DOUBLE, (ship, context) -> {
            if (ship == NO_SHIP) {
                return 0d;
            }
            double mass = ((ServerShip) ship).getInertiaData().getMass();
            org.joml.Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            double scalingVolume = scaling.x() * scaling.y() * scaling.z();
            return mass / scalingVolume;
        }));
    }

    private static Vec3 shipDirection(Ship ship, Direction dir) {
        var n = dir.getNormal();
        if (ship == NO_SHIP) return new Vec3(n.getX(), n.getY(), n.getZ());
        var v = new Vector3d(n.getX(), n.getY(), n.getZ());
        ship.getTransform().getShipToWorld().transformDirection(v);
        return new Vec3(v.x, v.y, v.z).normalize();
    }

    @SuppressWarnings("ConstantConditions")
    static Ship NO_SHIP = new Ship() {
        @Override
        public long getId() {
            return -1;
        }

        @Override
        public @NotNull String getSlug() {
            return "NONE";
        }

        @Override
        public @NotNull BodyKinematics getKinematics() {
            return null;
        }

        @Override
        public @NotNull ShipTransform getPrevTickTransform() {
            return null;
        }

        @Override
        public @NotNull ChunkClaim getChunkClaim() {
            return null;
        }

        @Override
        public @NotNull String getChunkClaimDimension() {
            return "";
        }

        @Override
        public @NotNull AABBdc getWorldAABB() {
            return new AABBd();
        }

        @Override
        public @NotNull AABBic getShipAABB() {
            return new AABBi();
        }

        @Override
        public @NotNull IShipActiveChunksSet getActiveChunksSet() {
            return null;
        }
    };
}
