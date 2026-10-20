package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.bodies.properties.BodyTransform;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.ClientShipTransformProvider;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VSClientCompat {

    /// Only call after verifying that VS is loaded
    static boolean isOnShip(ClientLevel level, BlockPos pos) {
        return VSGameUtilsKt.getLoadedShipManagingPos(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) != null;
    }

    /// Only call after verifying that VS is loaded
    static Vec3 toWorldRenderPos(ClientLevel level, Vec3 pos) {
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos.x, pos.y, pos.z);
        if (ship == null) return pos;
        Vector3d worldPos = ship.getRenderTransform().getShipToWorld()
                .transformPosition(new Vector3d(pos.x, pos.y, pos.z));
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }

    /// Only call after verifying that VS is loaded.
    /// Transforms pos (in its own grid's coordinates) into the local space of the grid managing
    /// anchorPos, using render transforms for smooth per-frame interpolation.
    static Vec3 toLocalRenderSpaceOf(ClientLevel level, BlockPos anchorPos, Vec3 pos) {
        Vec3 worldPos = toWorldRenderPos(level, pos);
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level,
                anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        if (ship == null) return worldPos;
        Vector3d local = ship.getRenderTransform().getWorldToShip()
                .transformPosition(new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        return new Vec3(local.x, local.y, local.z);
    }

    /// Only call after verifying that VS is loaded.
    /// The render transform of whichever ship manages pos when asked; the ship is looked up on
    /// every call, so a ship that loads after its blocks, or is reloaded, is picked up.
    static RenderTransformProvider renderTransformAt(ClientLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        return dest -> {
            ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, x, y, z);
            return ship == null ? null : dest.set(ship.getRenderTransform().getShipToWorld());
        };
    }

    /// Only call after verifying that VS is loaded.
    /// The ship-to-world transform of the ship managing pos, partialTick of the way from its last
    /// tick's transform (0) to this tick's (1). Null while pos is on no ship.
    static @Nullable Matrix4d renderTransformAt(ClientLevel level, BlockPos pos, float partialTick, Matrix4d dest) {
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return ship == null ? null : transformAt(ship, partialTick, dest);
    }

    /// Only call after verifying that VS is loaded.
    /// Every ship reaching into worldBounds, with its ship-to-world transform at each of the
    /// partial ticks into the current tick.
    static List<ClientCompat.GridPoses> gridsTouching(ClientLevel level, AABB worldBounds, float[] partialTicks) {
        List<ClientCompat.GridPoses> grids = new ArrayList<>();
        for (Ship ship : VSGameUtilsKt.getShipsIntersecting(level, worldBounds)) {
            Matrix4d[] poses = new Matrix4d[partialTicks.length];
            for (int i = 0; i < partialTicks.length; i++) {
                poses[i] = transformAt(ship, partialTicks[i], new Matrix4d());
            }
            AABBdc bounds = ship.getWorldAABB();
            grids.add(new ClientCompat.GridPoses(new UUID(0, ship.getId()),
                    new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()), poses));
        }
        return grids;
    }

    /// The ship's transform partialTick of the way from its last tick's (0) to this tick's (1), worked out as VS
    /// works out its render transform for a frame: by the ship's transform provider when it has one, otherwise
    /// by the same blend VS falls back on. getRenderTransform itself holds only the last frame's pose.
    private static Matrix4d transformAt(Ship ship, float partialTick, Matrix4d dest) {
        ShipTransform from = ship.getPrevTickTransform();
        ShipTransform to = ship.getTransform();
        ClientShipTransformProvider provider = ship instanceof ClientShip client ? client.getTransformProvider() : null;
        BodyTransform provided = provider == null ? null : provider.provideNextRenderTransform(from, to, partialTick);
        if (provided != null) {
            return dest.set(provided.getToWorld());
        }
        Vector3d inWorld = from.getPositionInWorld().lerp(to.getPositionInWorld(), partialTick, new Vector3d());
        Vector3d inShip = from.getPositionInShip().lerp(to.getPositionInShip(), partialTick, new Vector3d());
        Vector3d scaling = from.getShipToWorldScaling().lerp(to.getShipToWorldScaling(), partialTick, new Vector3d());
        Quaterniond rotation = from.getShipToWorldRotation().slerp(to.getShipToWorldRotation(), partialTick, new Quaterniond());
        return dest.translation(inWorld).rotate(rotation).scale(scaling).translate(inShip.negate());
    }
}
