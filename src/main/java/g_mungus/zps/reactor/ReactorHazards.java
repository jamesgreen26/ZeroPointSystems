package g_mungus.zps.reactor;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a lit reactor does to whatever is caught inside it. The chamber is far hotter than lava,
 * and lava is what it is treated as: every tick an entity spends in the cavity is a tick spent in
 * lava, with the same fire, the same damage, and the same immunities.
 *
 * <p>Entities live in the world whatever grid they stand in, so a reactor on a ship or a Sable
 * sublevel has its cavity taken out into the world to find them, and each one found is brought
 * back into the reactor's grid to see whether it is really in a cell of the cavity, rather than
 * merely inside the box the tilted cavity sweeps.
 */
public final class ReactorHazards {

    /** How far inside its box an entity has to reach: the same margin vanilla gives fluids. */
    private static final double SKIN = 1e-3;

    private ReactorHazards() {
    }

    /** Hurt everything inside the reactor's cavity as lava would, this tick. */
    static void hurtEntitiesInside(ServerLevel level, Reactor reactor) {
        GridSpace grid = Compat.gridOf(level, reactor.host());
        AABB search = grid == null ? reactor.bounds() : enclose(grid, reactor.bounds());
        for (Entity entity : level.getEntities((Entity) null, search, EntitySelector.NO_SPECTATORS)) {
            if (entity.isAlive() && touches(reactor, grid, entity.getBoundingBox())) {
                entity.lavaHurt();
            }
        }
    }

    /**
     * Whether an entity's box, given in the world, overlaps a cell of the cavity. Its corners and
     * centre are each looked up in the reactor's grid; the cavity is sealed, so an entity in it has
     * its centre in it, and the corners are what catches one clipping in through a wall.
     */
    static boolean touches(Reactor reactor, @Nullable GridSpace grid, AABB box) {
        AABB inner = box.deflate(SKIN);
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(
                    (corner & 1) == 0 ? inner.minX : inner.maxX,
                    (corner & 2) == 0 ? inner.minY : inner.maxY,
                    (corner & 4) == 0 ? inner.minZ : inner.maxZ);
            if (isInside(reactor, grid, point)) {
                return true;
            }
        }
        return isInside(reactor, grid, box.getCenter());
    }

    private static boolean isInside(Reactor reactor, @Nullable GridSpace grid, Vec3 world) {
        Vec3 local = grid == null ? world : grid.toLocal(world);
        return reactor.isInterior(BlockPos.containing(local));
    }

    /** A world box around a box given in the grid, however the grid is turned. */
    static AABB enclose(GridSpace grid, AABB local) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 world = grid.toWorld(new Vec3(
                    (corner & 1) == 0 ? local.minX : local.maxX,
                    (corner & 2) == 0 ? local.minY : local.maxY,
                    (corner & 4) == 0 ? local.minZ : local.maxZ));
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            maxZ = Math.max(maxZ, world.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
