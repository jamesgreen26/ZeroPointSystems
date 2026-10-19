package g_mungus.zps.client.exhaust;

import g_mungus.zps.compat.ClientCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The moving grids (Sable sublevels) exhaust can hit, gathered each tick. Their blocks join
 * {@link ExhaustField} like any other section, by plot section, and the stepping pass collides
 * particles with them in each grid's own block space.
 *
 * <p>Which grids, and which of their sections: the plumes' tracked sections are bucketed into
 * regions {@value #REGION_SECTIONS} sections a side, and each region's box is asked of Sable on
 * its own. A grid takes the plot sections under the part of each region it reaches into, and no
 * others; a grid near no plume is never picked, however close it is to another one.
 *
 * <p>Plot positions are around twenty million blocks out, far past where a float is precise, so a
 * grid's block space is taken relative to an anchor block near the part a plume reaches, as the
 * world's is relative to the simulation origin. The transforms are built in doubles and only then
 * narrowed. For each substep boundary the GPU gets each grid's transform both ways, so a particle's
 * motion and the hull's are tested as one, and a moving hull pushes smoke.
 *
 * <p>The grids go to the GPU as a texture buffer of RGBA32UI texels: texel 0 holds the count, then
 * per grid {@value #GRID_TEXELS} texels: the anchor, the bounds' low and high corners (relative to
 * the simulation origin), and for each of the {@code SUBSTEPS + 1} boundaries the world to grid
 * transform's four columns and then the grid to world one's. See {@code sim/step.vert}.
 *
 * <p>Render thread only.
 */
final class ExhaustGrids {

    /** Grids collided with at most. Beyond this, the ones reaching most plume regions win. */
    static final int MAX_GRIDS = 64;
    /** Sections a side of the regions the plumes are bucketed into for asking Sable. */
    private static final int REGION_SECTIONS = 4;
    /** Around a grid's bounds: smoke spreading and swirling near it, and its motion in a tick. */
    private static final double BOUNDS_MARGIN = 2;
    static final int GRID_TEXELS = 3 + (ExhaustSimulation.SUBSTEPS + 1) * 8;

    private static final float[] partialTicks = partialTicks();
    private static final LongSet wanted = new LongOpenHashSet();
    private static int glBuffer;
    private static int glTexture;
    private static ByteBuffer data;

    private ExhaustGrids() {
    }

    private static float[] partialTicks() {
        float[] partials = new float[ExhaustSimulation.SUBSTEPS + 1];
        for (int i = 0; i < partials.length; i++) {
            partials[i] = (float) i / ExhaustSimulation.SUBSTEPS;
        }
        return partials;
    }

    static int texture() {
        return glTexture;
    }

    /**
     * Gathers the grids near the plumes for this tick and uploads them, and returns every section
     * the field should hold: the plumes' world sections and those grids' plot sections under them.
     * The returned set is reused next tick.
     */
    static LongSet update(ClientLevel level, BlockPos simOrigin, LongSet worldSections) {
        allocate();
        wanted.clear();
        wanted.addAll(worldSections);

        // Each grid, with the plume regions it reaches into, in the order Sable first named it.
        Map<UUID, Reach> grids = new LinkedHashMap<>();
        for (AABB region : regions(worldSections)) {
            for (ClientCompat.GridPoses grid : ClientCompat.gridsTouching(level, region, partialTicks)) {
                grids.computeIfAbsent(grid.id(), id -> new Reach(grid)).regions.add(region);
            }
        }
        List<Reach> chosen = new ArrayList<>(grids.values());
        if (chosen.size() > MAX_GRIDS) {
            chosen.sort((a, b) -> Integer.compare(b.regions.size(), a.regions.size()));
            chosen = chosen.subList(0, MAX_GRIDS);
        }

        data.clear();
        MemoryUtil.memSet(data, 0);
        data.putInt(0, chosen.size());
        for (int g = 0; g < chosen.size(); g++) {
            write(g, chosen.get(g), simOrigin);
        }
        upload(1 + chosen.size() * GRID_TEXELS);
        return wanted;
    }

    private static final class Reach {
        final ClientCompat.GridPoses grid;
        final List<AABB> regions = new ArrayList<>();

        Reach(ClientCompat.GridPoses grid) {
            this.grid = grid;
        }
    }

    /** Puts a grid's plot sections under the plumes in the field, and its data in the buffer. */
    private static void write(int g, Reach reach, BlockPos simOrigin) {
        ClientCompat.GridPoses grid = reach.grid;
        Matrix4d worldToLocalNow = new Matrix4d(grid.localToWorld()[ExhaustSimulation.SUBSTEPS]).invert();
        AABB bounds = grid.worldBounds().inflate(BOUNDS_MARGIN);
        AABB reached = null;
        for (AABB region : reach.regions) {
            if (!bounds.intersects(region)) {
                continue;
            }
            AABB local = transform(bounds.intersect(region), worldToLocalNow);
            addSections(local);
            reached = reached == null ? local : reached.minmax(local);
        }
        BlockPos anchor = anchorOf(reached, bounds, worldToLocalNow);

        int base = (1 + g * GRID_TEXELS) * 16;
        data.putInt(base, anchor.getX()).putInt(base + 4, anchor.getY()).putInt(base + 8, anchor.getZ());
        data.putFloat(base + 16, (float) (bounds.minX - simOrigin.getX()))
                .putFloat(base + 20, (float) (bounds.minY - simOrigin.getY()))
                .putFloat(base + 24, (float) (bounds.minZ - simOrigin.getZ()));
        data.putFloat(base + 32, (float) (bounds.maxX - simOrigin.getX()))
                .putFloat(base + 36, (float) (bounds.maxY - simOrigin.getY()))
                .putFloat(base + 40, (float) (bounds.maxZ - simOrigin.getZ()));
        float[] columns = new float[16];
        for (int boundary = 0; boundary <= ExhaustSimulation.SUBSTEPS; boundary++) {
            // grid (anchor relative) to world (simulation origin relative), in doubles, then narrowed
            Matrix4d toWorld = new Matrix4d()
                    .translation(-simOrigin.getX(), -simOrigin.getY(), -simOrigin.getZ())
                    .mul(grid.localToWorld()[boundary])
                    .translate(anchor.getX(), anchor.getY(), anchor.getZ());
            int at = base + (3 + boundary * 8) * 16;
            new Matrix4f(new Matrix4d(toWorld).invert()).get(columns);
            for (int i = 0; i < 16; i++) {
                data.putFloat(at + i * 4, columns[i]);
            }
            new Matrix4f(toWorld).get(columns);
            for (int i = 0; i < 16; i++) {
                data.putFloat(at + 64 + i * 4, columns[i]);
            }
        }
    }

    /** Near the part of the grid the plumes reach, or its middle if none: where floats are kept small. */
    private static BlockPos anchorOf(AABB reached, AABB bounds, Matrix4d worldToLocal) {
        if (reached != null) {
            return BlockPos.containing(reached.getCenter());
        }
        Vector3d middle = worldToLocal.transformPosition(new Vector3d(bounds.getCenter().x, bounds.getCenter().y, bounds.getCenter().z));
        return BlockPos.containing(middle.x, middle.y, middle.z);
    }

    /** The plumes' sections bucketed into regions, as one box per region. */
    private static List<AABB> regions(LongSet sections) {
        Long2ObjectMap<int[]> buckets = new Long2ObjectOpenHashMap<>();
        for (LongIterator it = sections.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            int x = SectionPos.x(section);
            int y = SectionPos.y(section);
            int z = SectionPos.z(section);
            long bucket = SectionPos.asLong(Math.floorDiv(x, REGION_SECTIONS), Math.floorDiv(y, REGION_SECTIONS),
                    Math.floorDiv(z, REGION_SECTIONS));
            int[] box = buckets.get(bucket);
            if (box == null) {
                buckets.put(bucket, new int[]{x, y, z, x, y, z});
            } else {
                box[0] = Math.min(box[0], x);
                box[1] = Math.min(box[1], y);
                box[2] = Math.min(box[2], z);
                box[3] = Math.max(box[3], x);
                box[4] = Math.max(box[4], y);
                box[5] = Math.max(box[5], z);
            }
        }
        List<AABB> regions = new ArrayList<>(buckets.size());
        for (int[] box : buckets.values()) {
            regions.add(new AABB(box[0] * 16, box[1] * 16, box[2] * 16, box[3] * 16 + 16, box[4] * 16 + 16, box[5] * 16 + 16));
        }
        return regions;
    }

    private static void addSections(AABB local) {
        int minX = SectionPos.blockToSectionCoord(local.minX);
        int minY = SectionPos.blockToSectionCoord(local.minY);
        int minZ = SectionPos.blockToSectionCoord(local.minZ);
        int maxX = SectionPos.blockToSectionCoord(local.maxX);
        int maxY = SectionPos.blockToSectionCoord(local.maxY);
        int maxZ = SectionPos.blockToSectionCoord(local.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    wanted.add(SectionPos.asLong(x, y, z));
                }
            }
        }
    }

    /** The bounds of a box after a transform, from its eight corners. */
    private static AABB transform(AABB box, Matrix4d transform) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            transform.transformPosition((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ, corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // GL -----------------------------------------------------------------------------------------

    private static void allocate() {
        if (glBuffer != 0) {
            return;
        }
        data = MemoryUtil.memCalloc((1 + MAX_GRIDS * GRID_TEXELS) * 16);
        glBuffer = GL15.glGenBuffers();
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, glBuffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
        glTexture = GL11.glGenTextures();
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 12);
        int previousTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32UI, glBuffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousTexture);
        GL13.glActiveTexture(active);
    }

    private static void upload(int texels) {
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, glBuffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, data.limit(texels * 16));
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
        data.clear();
    }
}
