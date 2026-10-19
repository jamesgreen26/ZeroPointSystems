package g_mungus.zps.client.exhaust;

import com.mojang.blaze3d.platform.GlStateManager;
import g_mungus.zps.compat.ClientCompat;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.mixin.LevelRendererAccessor;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ClientTickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Every exhaust particle on the client, kept on the GPU as a ring of batch records: one record per
 * thruster per tick and per whole unit of its amount, each standing for up to as many particles as
 * the client config's {@code ExhaustParticlesPerTick}, which the shader works out from it (see
 * {@code assets/zps/flywheel/exhaust/batch.glsl}, which holds the layout). The CPU's cost is a
 * 96-byte record per batch, however many particles that is.
 *
 * <p>Records are in world space. An emitter on a moving grid hands over its own block space, and
 * the batch takes the grid's pose once, when it is placed; the particles never follow the grid.
 *
 * <p>The ring is overwritten oldest first, so once it is full a new batch takes the place of one
 * that may still be alive. Its size comes from the client config's {@code ExhaustParticlePool}.
 * Both are read when the ring is built; a change to the particles per tick rebuilds it at the next
 * emit, dropping the exhaust in the air.
 *
 * <p>Only the render thread touches the ring and the GL objects; {@link #slotsInUse()} is read
 * from Flywheel's frame workers.
 */
public final class ExhaustParticles {

    /** Ticks after which every particle of a batch is gone. Must match {@code ZPS_EXHAUST_MAX_AGE}. */
    private static final int MAX_LIFETIME_TICKS = 64;
    /**
     * Two texels ahead of the records. The first: x the batch size the shaders stride slots by, y 1
     * if particles tumble. The second: the simulation origin, and the tick last simulated. See
     * {@code common.glsl}.
     */
    private static final int HEADER_BYTES = 32;
    private static final int RECORD_BYTES = 96;
    /** Keeps the ring within the 65536 texels every driver's texture buffers take. */
    private static final int MAX_BATCHES = (65536 - 2) / 6;
    /** Most batches one emitter adds a tick, so a single emission cannot sweep the ring. */
    private static final float MAX_AMOUNT = 8;
    /** Beyond this many blocks from the camera an emitter adds nothing; what is in the air stays. */
    private static final double EMIT_DISTANCE = 128;
    /** Above every unit Flywheel and vanilla bind, and within the 16 every GL 4.1 driver offers. */
    private static final int TEXTURE_UNIT = 15;
    private static final String SAMPLER = "zps_exhaustBatches";

    private static ByteBuffer records;
    /** Particles per batch, the ring's stride; 0 before the ring is built. */
    private static int batchSize;
    private static int capacity;
    private static int glBuffer;
    private static int glTexture;

    private static ClientLevel level;
    private static ExhaustEffect effect;
    /** The manager {@link #effect} was added to; Flywheel replaces it, effects and all, on a reload. */
    private static VisualizationManager effectManager;
    private static int nextBatch;
    private static int batchesUsed;
    private static volatile int slotsInUse;
    private static int lastEmitTick;
    private static int seedCounter;
    private static final List<Emission> pending = new ArrayList<>();
    private static final Matrix4d transformFrom = new Matrix4d();
    private static final Matrix4d transformTo = new Matrix4d();

    /** Must match the drag in batch.glsl: how far out the plume comes to rest is speed / drag. */
    private static final float DRAG = 0.1f;
    /** Spread, nozzle jitter, swirl and cube size around the plume's centre line, with room to spare. */
    private static final double PLUME_MARGIN = 6;
    /** How far smoke can rise under buoyancy over a lifetime. */
    private static final double PLUME_RISE = 4;
    private static final Long2IntMap sectionLastUse = new Long2IntOpenHashMap();
    static {
        sectionLastUse.defaultReturnValue(Integer.MIN_VALUE);
    }
    private static volatile LongSet trackedSections = LongSet.of();
    private static boolean sectionsChanged;

    private ExhaustParticles() {
    }

    /** Whether exhaust can be drawn in this level at all: it needs a Flywheel backend. */
    public static boolean available(ClientLevel level) {
        return VisualizationManager.supportsVisualization(level);
    }

    /**
     * Emits one tick's batch from an emitter, given in its own block's space: a block on a moving
     * grid gives positions in the grid's block space. The batch is placed in the world at the end
     * of the tick, once the grid's pose for the tick is known; see {@link #onClientTick}.
     *
     * @param pos     the emitting block, which says which grid, if any, the emitter is on
     * @param exhaust what leaves this tick, see {@link ThrusterExhaustEmitter.ExhaustParams}
     */
    public static void emit(ClientLevel emitterLevel, BlockPos pos, ThrusterExhaustEmitter.ExhaustParams exhaust) {
        if (emitterLevel != level) {
            reset();
            level = emitterLevel;
        }
        pending.add(new Emission(pos, exhaust));
    }

    private record Emission(BlockPos pos, ThrusterExhaustEmitter.ExhaustParams exhaust) {
    }

    /**
     * Places a pending emission in the world and writes its records: one batch per whole unit of
     * amount, the last taking what is left, so an amount over 1 is more batches from the same
     * nozzle in the same tick. On a moving grid it is placed
     * as the grid is drawn through the coming tick: the nozzle where it is drawn at partial tick 0,
     * moving at its drawn displacement to partial tick 1, so the batch's staggered births follow the
     * nozzle exactly. Its cubes start square to the grid; after that, position and turn are the
     * world's alone.
     */
    private static void place(Emission emission, int tick) {
        Matrix4d from = ClientCompat.renderTransformAt(level, emission.pos(), 0f, transformFrom);
        Matrix4d to = from == null ? null : ClientCompat.renderTransformAt(level, emission.pos(), 1f, transformTo);
        ThrusterExhaustEmitter.ExhaustParams exhaust = emission.exhaust();
        Vector3d start = new Vector3d(exhaust.position().x, exhaust.position().y, exhaust.position().z);
        Vector3d end = new Vector3d(start);
        Vector3d direction = new Vector3d(exhaust.direction().x, exhaust.direction().y, exhaust.direction().z);
        Quaterniond orientation = new Quaterniond();
        if (from != null && to != null) {
            from.transformPosition(start);
            to.transformPosition(end);
            from.transformDirection(direction).normalize();
            from.getNormalizedRotation(orientation);
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(start.x, start.y, start.z) > EMIT_DISTANCE * EMIT_DISTANCE) {
            return;
        }

        allocate();
        // A fractional amount takes a share of a batch's slots; the rest stay empty.
        int particles = Math.round(Math.clamp(exhaust.amount(), 0f, MAX_AMOUNT) * batchSize);
        if (particles == 0) {
            return;
        }
        int tumble = ZPSConfig.exhaustTumble() ? 1 : 0;
        if (records.getInt(4) != tumble) {
            records.putInt(4, tumble);
        }
        BlockPos anchor = BlockPos.containing(start.x, start.y, start.z);
        Vec3 offset = new Vec3(start.x - anchor.getX(), start.y - anchor.getY(), start.z - anchor.getZ());
        Vec3 velocity = new Vec3(end.x - start.x, end.y - start.y, end.z - start.z);
        Vec3 worldDirection = new Vec3(direction.x, direction.y, direction.z);
        if (batchesUsed == 0) {
            // The simulation keeps positions relative to a block fixed while the ring holds anything.
            records.putInt(16, anchor.getX()).putInt(20, anchor.getY()).putInt(24, anchor.getZ());
        }
        for (int written = 0; written < particles; written += batchSize) {
            int base = HEADER_BYTES + nextBatch * RECORD_BYTES;
            records.putInt(base, anchor.getX())
                    .putInt(base + 4, anchor.getY())
                    .putInt(base + 8, anchor.getZ())
                    .putInt(base + 12, tick)
                    .putFloat(base + 16, (float) offset.x)
                    .putFloat(base + 20, (float) offset.y)
                    .putFloat(base + 24, (float) offset.z)
                    .putInt(base + 28, HashCommon.murmurHash3(++seedCounter))
                    .putFloat(base + 32, (float) worldDirection.x)
                    .putFloat(base + 36, (float) worldDirection.y)
                    .putFloat(base + 40, (float) worldDirection.z)
                    .putInt(base + 44, Math.min(particles - written, batchSize))
                    .putFloat(base + 48, (float) velocity.x)
                    .putFloat(base + 52, (float) velocity.y)
                    .putFloat(base + 56, (float) velocity.z)
                    .putFloat(base + 60, exhaust.speed())
                    .putFloat(base + 64, (float) orientation.x)
                    .putFloat(base + 68, (float) orientation.y)
                    .putFloat(base + 72, (float) orientation.z)
                    .putFloat(base + 76, (float) orientation.w)
                    .putFloat(base + 80, exhaust.width())
                    .putInt(base + 84, exhaust.color().ordinal())
                    .putFloat(base + 88, Math.clamp(exhaust.coldFraction(), 0f, 1f));
            nextBatch = (nextBatch + 1) % capacity;
            if (batchesUsed < capacity) {
                batchesUsed++;
                slotsInUse = batchesUsed * batchSize;
            }
        }
        lastEmitTick = tick;
        trackPlume(new Vec3(start.x, start.y, start.z), worldDirection, exhaust.speed(), exhaust.width(), velocity, tick);
        if (effect == null) {
            addEffect(VisualizationManager.getOrThrow(level));
        }
    }

    private static void addEffect(VisualizationManager manager) {
        effect = new ExhaustEffect(level);
        effectManager = manager;
        manager.effects().queueAdd(effect);
    }

    /** Whether there is exhaust in the air, or about to be, for the layer to draw. */
    static boolean active() {
        return effect != null;
    }

    /** How many instances the effect should have: every slot of every batch used since it went idle. */
    static int slotsInUse() {
        return slotsInUse;
    }

    /** The sections live particles can be in, for Flywheel to keep their light. Never modified once published. */
    static LongSet trackedSections() {
        return trackedSections;
    }

    /**
     * Marks every section the batch's plume can reach. Generous on purpose: a particle in an
     * untracked section draws black. Drag acts on all of a particle's velocity, its own and the
     * emitter's, so the plume runs at most {@code (speed * direction + velocity) / drag} from the
     * nozzle, and spreads, swirls and rises a few blocks around that line, and around the
     * nozzle's own width. The line is marked in
     * steps shorter than the margin rather than as one box: a long diagonal plume from a fast
     * grid would otherwise take every section of the cube around it.
     */
    private static void trackPlume(Vec3 nozzle, Vec3 direction, float speed, float width, Vec3 velocity, int tick) {
        Vec3 travel = direction.scale(speed).add(velocity).scale(1 / DRAG);
        double margin = PLUME_MARGIN + width / 2;
        int steps = Math.max(1, (int) Math.ceil(travel.length() / margin));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = nozzle.add(travel.scale((double) i / steps));
            trackBox(new AABB(at, at).inflate(margin).expandTowards(0, PLUME_RISE, 0), tick);
        }
    }

    private static void trackBox(AABB box, int tick) {
        int minX = SectionPos.blockToSectionCoord(box.minX);
        int minY = SectionPos.blockToSectionCoord(box.minY);
        int minZ = SectionPos.blockToSectionCoord(box.minZ);
        int maxX = SectionPos.blockToSectionCoord(box.maxX);
        int maxY = SectionPos.blockToSectionCoord(box.maxY);
        int maxZ = SectionPos.blockToSectionCoord(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (sectionLastUse.put(SectionPos.asLong(x, y, z), tick) == sectionLastUse.defaultReturnValue()) {
                        sectionsChanged = true;
                    }
                }
            }
        }
    }

    /** Lets go of sections no live particle can be in any more, and publishes the set if it moved. */
    private static void expireSections(int tick) {
        sectionsChanged |= sectionLastUse.values().removeIf(last -> tick - last > MAX_LIFETIME_TICKS);
        if (sectionsChanged) {
            trackedSections = new LongOpenHashSet(sectionLastUse.keySet());
            sectionsChanged = false;
        }
    }

    /**
     * Places this tick's emissions, uploads the records, and drops the effect once its last
     * particle has faded. At the end of the tick, so that every grid has its pose for the tick.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pending.isEmpty()) {
            int tick = ticks();
            for (Emission emission : pending) {
                place(emission, tick);
            }
            pending.clear();
        }
        if (effect == null) {
            return;
        }
        if (ticks() - lastEmitTick > MAX_LIFETIME_TICKS) {
            clearRing();
            return;
        }
        // A resource reload or backend change makes Flywheel start the level's visualization
        // afresh. Block entities and entities are found again; an effect is not, so the ring's
        // particles get a new one, which makes instances for every slot in use.
        VisualizationManager manager = VisualizationManager.get(level);
        if (manager != effectManager && manager != null) {
            addEffect(manager);
        }
        int tick = ticks();
        expireSections(tick);
        BlockPos simOrigin = new BlockPos(records.getInt(16), records.getInt(20), records.getInt(24));
        ExhaustField.sync(level, ExhaustGrids.update(level, simOrigin, sectionLastUse.keySet()));
        records.putInt(28, tick);
        upload();
        ExhaustSimulation.step(tick, glTexture, batchSize);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == level) {
            reset();
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** A block changed on the client: the terrain exhaust collides with follows it. */
    public static void onBlockChanged(BlockPos pos, BlockState state) {
        ExhaustField.onBlockChanged(pos, state);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        ExhaustField.onChunkLoad(event);
    }

    /** Resources reloaded: the simulation's shaders are rebuilt from them on next use. */
    public static void onResourcesReloaded() {
        ExhaustSimulation.onResourcesReloaded();
    }

    /**
     * Points a Flywheel program's batch sampler at the ring, called each time Flywheel binds one.
     * Programs that do not draw exhaust have no such uniform and are left alone.
     */
    public static void bindTo(GlProgram program) {
        if (glTexture == 0 || program.getUniformLocation(SAMPLER) < 0) {
            return;
        }
        program.setSamplerBinding(SAMPLER, TEXTURE_UNIT);
        int previous = GlStateManager._getActiveTexture();
        // Raw GL: GlStateManager only tracks units below 12 and would index past its table.
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
        GL13.glActiveTexture(previous);
        ExhaustSimulation.bindForDrawing(program);
    }

    /** The particles' clock: Flywheel's {@code flw_ticks}, which stops while the game is paused. */
    private static int ticks() {
        return ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).zps$getTicks();
    }

    /** Builds the ring, or rebuilds it if the configured particles per tick have changed. */
    private static void allocate() {
        int wanted = ZPSConfig.exhaustParticlesPerTick();
        if (records != null && wanted == batchSize) {
            return;
        }
        if (records != null) {
            // Live instances are laid out at the old stride.
            clearRing();
            MemoryUtil.memFree(records);
        }
        batchSize = wanted;
        // The simulation's state is shown to the drawing as one texture buffer, as long as the
        // driver allows.
        int maxBatches = Math.min(MAX_BATCHES, ExhaustSimulation.maxSlots() / batchSize);
        capacity = Math.clamp(ZPSConfig.exhaustParticlePool() / batchSize, 1, maxBatches);
        ExhaustSimulation.allocate(capacity * batchSize);
        records = MemoryUtil.memCalloc(HEADER_BYTES + capacity * RECORD_BYTES);
        records.putInt(0, batchSize);
        if (glBuffer == 0) {
            glBuffer = GL15.glGenBuffers();
        }
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, glBuffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, records, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
        if (glTexture == 0) {
            glTexture = GL11.glGenTextures();
        }
        // Attached again whenever the buffer is respecified, as ExhaustSimulation does for its
        // state: a driver may keep the old store's size for a texture buffer that is not.
        int previousUnit = GlStateManager._getActiveTexture();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32UI, glBuffer);
        GL13.glActiveTexture(previousUnit);
    }

    private static void upload() {
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, glBuffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, records);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
    }

    /** Forgets the level along with its exhaust. The ring and GL objects are kept for the next. */
    private static void reset() {
        clearRing();
        pending.clear();
        ExhaustField.clear();
        level = null;
    }

    /**
     * Drops every particle and the effect drawing them. Flywheel drops a level's visuals with it, so
     * the effect is only queued for removal if the level is still being drawn.
     */
    private static void clearRing() {
        if (effect != null) {
            VisualizationManager manager = VisualizationManager.get(level);
            if (manager != null) {
                manager.effects().queueRemove(effect);
            }
        }
        effect = null;
        effectManager = null;
        nextBatch = 0;
        batchesUsed = 0;
        slotsInUse = 0;
        sectionLastUse.clear();
        trackedSections = LongSet.of();
        sectionsChanged = false;
        if (records != null) {
            MemoryUtil.memSet(MemoryUtil.memAddress(records) + HEADER_BYTES, 0, (long) capacity * RECORD_BYTES);
            upload();
        }
    }
}
