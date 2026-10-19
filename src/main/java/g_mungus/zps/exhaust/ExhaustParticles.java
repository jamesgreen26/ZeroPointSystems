package g_mungus.zps.exhaust;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Every exhaust particle on the client, kept on the GPU as a ring of batch records: one record per
 * thruster per tick, each standing for as many particles as the client config's
 * {@code ExhaustParticlesPerTick}, which the shader works out from it (see
 * {@code assets/zps/flywheel/exhaust/batch.glsl}, which holds the layout). The CPU's cost is one
 * 64-byte record per firing thruster per tick, however many particles that is.
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
     * One texel ahead of the records: x is the batch size the shader strides slots by, y is 1 if
     * particles tumble.
     */
    private static final int HEADER_BYTES = 16;
    private static final int RECORD_BYTES = 64;
    /** Keeps the ring within the 65536 texels every driver's texture buffers take. */
    private static final int MAX_BATCHES = (65536 - 1) / 4;
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
    private static int nextBatch;
    private static int batchesUsed;
    private static volatile int slotsInUse;
    private static int lastEmitTick;
    private static int seedCounter;
    private static boolean dirty;

    /** Must match the drag in batch.glsl: how far out the plume comes to rest is speed / drag. */
    private static final float DRAG = 0.1f;
    /** Spread, nozzle jitter, swirl and cube size around the plume's centre line, with room to spare. */
    private static final double PLUME_MARGIN = 4;
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
     * Emits one tick's batch.
     *
     * @param anchor    the block the batch is placed relative to, kept exact as integers
     * @param offset    the nozzle's position relative to {@code anchor}
     * @param direction unit exhaust direction
     * @param speed     exhaust speed leaving the nozzle, blocks per tick
     * @param velocity  the emitter's own velocity, blocks per tick, which the particles inherit
     */
    public static void emit(ClientLevel emitterLevel, BlockPos anchor, Vec3 offset, Vec3 direction, float speed,
                            Vec3 velocity) {
        if (emitterLevel != level) {
            reset();
            level = emitterLevel;
        }
        allocate();
        int tumble = ZPSConfig.exhaustTumble() ? 1 : 0;
        if (records.getInt(4) != tumble) {
            records.putInt(4, tumble);
        }
        int tick = ticks();
        int base = HEADER_BYTES + nextBatch * RECORD_BYTES;
        records.putInt(base, anchor.getX())
                .putInt(base + 4, anchor.getY())
                .putInt(base + 8, anchor.getZ())
                .putInt(base + 12, tick)
                .putFloat(base + 16, (float) offset.x)
                .putFloat(base + 20, (float) offset.y)
                .putFloat(base + 24, (float) offset.z)
                .putInt(base + 28, HashCommon.murmurHash3(++seedCounter))
                .putFloat(base + 32, (float) direction.x)
                .putFloat(base + 36, (float) direction.y)
                .putFloat(base + 40, (float) direction.z)
                .putInt(base + 44, batchSize)
                .putFloat(base + 48, (float) velocity.x)
                .putFloat(base + 52, (float) velocity.y)
                .putFloat(base + 56, (float) velocity.z)
                .putFloat(base + 60, speed);
        nextBatch = (nextBatch + 1) % capacity;
        if (batchesUsed < capacity) {
            batchesUsed++;
            slotsInUse = batchesUsed * batchSize;
        }
        lastEmitTick = tick;
        dirty = true;
        trackPlume(Vec3.atLowerCornerOf(anchor).add(offset), direction, speed, velocity, tick);
        if (effect == null) {
            effect = new ExhaustEffect(level);
            VisualizationManager.getOrThrow(level).effects().queueAdd(effect);
        }
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
     * untracked section draws black. The plume runs {@code speed / drag} along the exhaust, carried
     * by the emitter's velocity, and spreads, swirls and rises a few blocks around that line.
     */
    private static void trackPlume(Vec3 nozzle, Vec3 direction, float speed, Vec3 velocity, int tick) {
        Vec3 end = nozzle.add(direction.scale(speed / DRAG)).add(velocity.scale(MAX_LIFETIME_TICKS));
        AABB plume = new AABB(nozzle, end).inflate(PLUME_MARGIN).expandTowards(0, PLUME_RISE, 0);
        int minX = SectionPos.blockToSectionCoord(plume.minX);
        int minY = SectionPos.blockToSectionCoord(plume.minY);
        int minZ = SectionPos.blockToSectionCoord(plume.minZ);
        int maxX = SectionPos.blockToSectionCoord(plume.maxX);
        int maxY = SectionPos.blockToSectionCoord(plume.maxY);
        int maxZ = SectionPos.blockToSectionCoord(plume.maxZ);
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

    /** Uploads this tick's records, and drops the effect once its last particle has faded. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (effect == null) {
            return;
        }
        if (ticks() - lastEmitTick > MAX_LIFETIME_TICKS) {
            clearRing();
            return;
        }
        expireSections(ticks());
        if (dirty) {
            upload();
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == level) {
            reset();
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
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
        capacity = Math.clamp(ZPSConfig.exhaustParticlePool() / batchSize, 1, MAX_BATCHES);
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
            int previousUnit = GlStateManager._getActiveTexture();
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32UI, glBuffer);
            GL13.glActiveTexture(previousUnit);
        }
    }

    private static void upload() {
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, glBuffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, records);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
        dirty = false;
    }

    /** Forgets the level along with its exhaust. The ring and GL objects are kept for the next. */
    private static void reset() {
        clearRing();
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
