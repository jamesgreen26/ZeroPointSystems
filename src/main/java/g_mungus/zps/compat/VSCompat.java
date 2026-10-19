package g_mungus.zps.compat;

import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
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

    private static boolean isStatic(Ship ship) {
        return ship instanceof ServerShip serverShip? serverShip.isStatic() : ship.getPrevTickTransform().equals(ship.getTransform());
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
            if (isStatic(ship)) return Vec3.ZERO;
            Vector3d arm = VectorConversionsMCKt.toJOML(world).sub(ship.getTransform().getPositionInWorld());
            Vector3d velocity = new Vector3d(ship.getAngularVelocity()).cross(arm).add(ship.getVelocity());
            return VectorConversionsMCKt.toMinecraft(velocity.div(20.0));
        }

        @Override
        public boolean isSameGrid(GridSpace other) {
            return other instanceof ShipSpace otherSpace && otherSpace.ship().getId() == ship.getId();
        }
    }

    static void registerScriptTypes(RegisterScriptTypesEvent event) {
        ShipScripts.registerScriptTypes(event);
    }

    static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        ShipScripts.registerScriptCommands(event, context -> {
            Ship ship = VSGameUtilsKt.getShipManagingPos(context.level(), context.pos());
            return ship == null ? null : new ShipView(ship);
        }, null);
    }

    /** A ship as the server tick sees it, in {@link GridPhysics}' units like its physics step. */
    private record ShipView(Ship ship) implements ShipScripts.ShipView {
        @Override
        public String name() {
            return ship.getSlug();
        }

        @Override
        public String id() {
            return Long.toString(ship.getId());
        }

        /** The ship's extent in the world, scaled as it is. */
        @Override
        public Vec3 boundingBox() {
            AABBic aabb = ship.getShipAABB();
            if (aabb == null) {
                return Vec3.ZERO;
            }
            return new Vec3(
                    aabb.maxX() - aabb.minX(),
                    aabb.maxY() - aabb.minY(),
                    aabb.maxZ() - aabb.minZ()
            ).scale(ship.getTransform().getShipToWorldScaling().x());
        }

        @Override
        public Vec3 position() {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInWorld());
        }

        @Override
        public Vec3 worldVelocity() {
            return VectorConversionsMCKt.toMinecraft(ship.getVelocity());
        }

        @Override
        public Vec3 angularVelocity() {
            return VectorConversionsMCKt.toMinecraft(ship.getAngularVelocity());
        }

        @Override
        public Vec3 centreOfMass() {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInShip());
        }

        @Override
        public double mass() {
            return ship instanceof ServerShip server ? server.getInertiaData().getMass() / VSGridPhysics.KG_PER_BLOCK : 0;
        }

        @Override
        public Vec3 toWorldDirection(Vec3 direction) {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform()
                    .transformDirectionNoScalingFromShipToWorld(VectorConversionsMCKt.toJOML(direction), new Vector3d()));
        }

        @Override
        public Vec3 toGridDirection(Vec3 direction) {
            return VectorConversionsMCKt.toMinecraft(ship.getTransform()
                    .transformDirectionNoScalingFromWorldToShip(VectorConversionsMCKt.toJOML(direction), new Vector3d()));
        }
    }
}
