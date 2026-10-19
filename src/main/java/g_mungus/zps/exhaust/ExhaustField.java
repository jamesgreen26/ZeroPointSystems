package g_mungus.zps.exhaust;

import g_mungus.zps.ZPSMod;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * What exhaust particles move through: for each chunk section a plume can reach, the terrain at
 * four sub-cells a side per block, and a per-block count of particles that ExhaustSimulation fills
 * each substep for the push of crowding. The layout is described in {@code sim/field.glsl}.
 *
 * <p>The sections are the ones ExhaustParticles already tracks for light, so the field holds just
 * where plumes are, at any distance. A section's terrain is built from the blocks' collision
 * shapes when it joins, a few sections a tick, and kept up from block changes and chunk loads;
 * a section not built yet, or one that found no free slot, is air to the particles.
 *
 * <p>Render thread only.
 */
final class ExhaustField {

    private static final int INDEX = 32;
    private static final int SLOT_GRID = 8;
    private static final int MAX_SLOTS = SLOT_GRID * SLOT_GRID * SLOT_GRID;
    private static final int TERRAIN_SIZE = SLOT_GRID * 16;
    static final int DENSITY_WIDTH = 2048;
    static final int DENSITY_HEIGHT = 1024;
    /** Sections built per tick, so a plume swinging into new ground costs a little each tick. */
    private static final int BUILDS_PER_TICK = 12;
    private static final long FULL = -1L;

    private static int indexTexture;
    private static int terrainTexture;
    private static int densityTexture;
    private static int densityFramebuffer;

    private static @Nullable ClientLevel level;
    private static final Long2IntMap slots = new Long2IntOpenHashMap();
    private static final IntArrayFIFOQueue freeSlots = new IntArrayFIFOQueue();
    private static final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private static boolean warnedFull;
    /** Sub-cell masks of block states whose collision shape is the same everywhere. */
    private static final Reference2LongMap<BlockState> masks = new Reference2LongOpenHashMap<>();
    private static @Nullable ByteBuffer sectionUpload;
    private static @Nullable IntBuffer entryUpload;

    static {
        slots.defaultReturnValue(-1);
    }

    private ExhaustField() {
    }

    static boolean allocated() {
        return indexTexture != 0;
    }

    /** Brings the field in line with the sections a plume can reach. */
    static void sync(ClientLevel syncLevel, LongSet wanted) {
        if (level != syncLevel) {
            clear();
            level = syncLevel;
        }
        allocate();
        for (LongIterator it = slots.keySet().iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (!wanted.contains(section)) {
                int slot = slots.get(section);
                it.remove();
                writeEntry(section, 0, 0, 0, 0);
                freeSlots.enqueue(slot);
            }
        }
        pending.removeIf(section -> !wanted.contains(section));
        for (LongIterator it = wanted.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (!slots.containsKey(section)) {
                pending.add(section);
            }
        }
        for (int built = 0; built < BUILDS_PER_TICK && !pending.isEmpty(); built++) {
            long section = pending.removeFirstLong();
            if (!slots.containsKey(section)) {
                admit(section);
            } else {
                build(section, slots.get(section));
            }
        }
    }

    /** Drops every section; the textures are kept. */
    static void clear() {
        if (allocated()) {
            for (long section : slots.keySet()) {
                writeEntry(section, 0, 0, 0, 0);
            }
        }
        slots.clear();
        pending.clear();
        freeSlots.clear();
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            freeSlots.enqueue(slot);
        }
        masks.clear();
        level = null;
    }

    /** A block changed: its sub-cells follow if its section is in the field. */
    static void onBlockChanged(BlockPos pos, BlockState state) {
        int slot = slots.get(SectionPos.asLong(pos));
        if (slot < 0 || level == null) {
            return;
        }
        ByteBuffer texel = MemoryUtil.memAlloc(8);
        try {
            texel.putLong(0, mask(level, state, pos));
            upload(GL12.GL_TEXTURE_3D, terrainTexture, () -> GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0,
                    slotX(slot) * 16 + (pos.getX() & 15), slotY(slot) * 16 + (pos.getY() & 15),
                    slotZ(slot) * 16 + (pos.getZ() & 15), 1, 1, 1, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, texel));
        } finally {
            MemoryUtil.memFree(texel);
        }
    }

    /** A chunk arrived: any of its sections in the field are built again from what it holds. */
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() != level) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        for (long section : slots.keySet()) {
            if (SectionPos.x(section) == chunk.x && SectionPos.z(section) == chunk.z) {
                pending.add(section);
            }
        }
    }

    // Binding ------------------------------------------------------------------------------------

    static int indexTexture() {
        return indexTexture;
    }

    static int terrainTexture() {
        return terrainTexture;
    }

    static int densityTexture() {
        return densityTexture;
    }

    static int densityFramebuffer() {
        return densityFramebuffer;
    }

    // Sections -----------------------------------------------------------------------------------

    private static void admit(long section) {
        if (freeSlots.isEmpty()) {
            if (!warnedFull) {
                warnedFull = true;
                ZPSMod.LOGGER.warn("Exhaust field is full ({} sections); exhaust passes through terrain beyond it", MAX_SLOTS);
            }
            return;
        }
        int x = Math.floorMod(SectionPos.x(section), INDEX);
        int y = Math.floorMod(SectionPos.y(section), INDEX);
        int z = Math.floorMod(SectionPos.z(section), INDEX);
        for (long other : slots.keySet()) {
            if (Math.floorMod(SectionPos.x(other), INDEX) == x && Math.floorMod(SectionPos.y(other), INDEX) == y
                    && Math.floorMod(SectionPos.z(other), INDEX) == z) {
                // Another section a whole index away shares the cell; plumes never span that far.
                return;
            }
        }
        int slot = freeSlots.dequeueInt();
        slots.put(section, slot);
        build(section, slot);
        writeEntry(section, slot + 1, SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
    }

    /** Fills a slot with a section's sub-cells, read from its chunk as loaded. */
    private static void build(long section, int slot) {
        ByteBuffer data = sectionUpload;
        data.clear();
        MemoryUtil.memSet(data, 0);
        ClientLevel source = level;
        int sx = SectionPos.x(section);
        int sy = SectionPos.y(section);
        int sz = SectionPos.z(section);
        LevelChunk chunk = source.getChunkSource().getChunk(sx, sz, false);
        int sectionIndex = source.getSectionIndexFromSectionY(sy);
        if (chunk != null && sectionIndex >= 0 && sectionIndex < chunk.getSectionsCount()) {
            LevelChunkSection blocks = chunk.getSection(sectionIndex);
            if (!blocks.hasOnlyAir()) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = blocks.getBlockState(x, y, z);
                            if (!state.isAir()) {
                                pos.set(sx * 16 + x, sy * 16 + y, sz * 16 + z);
                                data.putLong(((z * 16 + y) * 16 + x) * 8, mask(source, state, pos));
                            }
                        }
                    }
                }
            }
        }
        upload(GL12.GL_TEXTURE_3D, terrainTexture, () -> GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0,
                slotX(slot) * 16, slotY(slot) * 16, slotZ(slot) * 16, 16, 16, 16,
                GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, data));
    }

    /**
     * Which of a block's 64 sub-cells its collision shape reaches into, bit x + 4z + 16y. A
     * sub-cell counts if the shape overlaps it at all, so thin things like panes still stop
     * particles. Cached per state when the shape cannot depend on where the block is.
     */
    private static long mask(ClientLevel source, BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return 0L;
        }
        boolean cacheable = !state.getBlock().hasDynamicShape() && !state.hasOffsetFunction();
        if (cacheable && masks.containsKey(state)) {
            return masks.getLong(state);
        }
        long mask = shapeMask(state.getCollisionShape(source, pos));
        if (cacheable) {
            masks.put(state, mask);
        }
        return mask;
    }

    private static long shapeMask(VoxelShape shape) {
        if (shape.isEmpty()) {
            return 0L;
        }
        if (shape == Shapes.block()) {
            return FULL;
        }
        long mask = 0L;
        for (AABB box : shape.toAabbs()) {
            int x0 = Math.max(0, (int) Math.floor(box.minX * 4.0));
            int x1 = Math.min(3, (int) Math.ceil(box.maxX * 4.0) - 1);
            int y0 = Math.max(0, (int) Math.floor(box.minY * 4.0));
            int y1 = Math.min(3, (int) Math.ceil(box.maxY * 4.0) - 1);
            int z0 = Math.max(0, (int) Math.floor(box.minZ * 4.0));
            int z1 = Math.min(3, (int) Math.ceil(box.maxZ * 4.0) - 1);
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        mask |= 1L << (x + 4 * z + 16 * y);
                    }
                }
            }
        }
        return mask;
    }

    private static int slotX(int slot) {
        return slot % SLOT_GRID;
    }

    private static int slotY(int slot) {
        return slot / SLOT_GRID % SLOT_GRID;
    }

    private static int slotZ(int slot) {
        return slot / (SLOT_GRID * SLOT_GRID);
    }

    // GL -----------------------------------------------------------------------------------------

    private static void writeEntry(long section, int slotPlusOne, int x, int y, int z) {
        IntBuffer entry = entryUpload;
        entry.clear();
        entry.put(slotPlusOne).put(x).put(y).put(z).flip();
        upload(GL12.GL_TEXTURE_3D, indexTexture, () -> GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0,
                Math.floorMod(SectionPos.x(section), INDEX), Math.floorMod(SectionPos.y(section), INDEX),
                Math.floorMod(SectionPos.z(section), INDEX), 1, 1, 1, GL30.GL_RGBA_INTEGER, GL11.GL_INT, entry));
    }

    /** Runs an upload with a texture bound raw, putting back what was bound. */
    private static void upload(int target, int texture, Runnable upload) {
        int previous = GL11.glGetInteger(target == GL12.GL_TEXTURE_3D ? GL12.GL_TEXTURE_BINDING_3D : GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(target, texture);
        withPlainUnpack(upload);
        GL11.glBindTexture(target, previous);
    }

    /**
     * Runs a texture upload or allocation with the unpack state as GL starts it: tightly packed
     * rows read from client memory. Unpack state is global and others leave it set (vanilla's
     * image uploads leave a row length and skips behind; a mod may leave a pixel unpack buffer
     * bound), and the driver then reads our data with the wrong stride, or as an offset into
     * someone's buffer. Everything is put back as found.
     */
    static void withPlainUnpack(Runnable upload) {
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int imageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int skipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
        int unpackBuffer = GL11.glGetInteger(GL30.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL30.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        try {
            upload.run();
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
            GL30.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static void allocate() {
        if (allocated()) {
            return;
        }
        sectionUpload = MemoryUtil.memAlloc(16 * 16 * 16 * 8);
        entryUpload = MemoryUtil.memAllocInt(4);
        indexTexture = texture3D(GL30.GL_RGBA32I, GL30.GL_RGBA_INTEGER, GL11.GL_INT, INDEX);
        terrainTexture = texture3D(GL30.GL_RG32UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, TERRAIN_SIZE);

        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        densityTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, densityTexture);
        withPlainUnpack(() -> GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16F, DENSITY_WIDTH, DENSITY_HEIGHT,
                0, GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null));
        nearest(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        densityFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, densityFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, densityTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            ZPSMod.LOGGER.error("Exhaust density framebuffer incomplete: 0x{}", Integer.toHexString(status));
        }
        freeSlots.clear();
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            freeSlots.enqueue(slot);
        }
    }

    /** A zeroed integer 3D texture: zero is "no section" in the index and "air" in the terrain. */
    private static int texture3D(int internalFormat, int format, int type, int size) {
        int previous = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        int bytes = internalFormat == GL30.GL_RGBA32I ? 16 : 8;
        ByteBuffer zeros = MemoryUtil.memCalloc(size * size * size * bytes);
        withPlainUnpack(() -> GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, internalFormat, size, size, size, 0, format,
                type, zeros));
        MemoryUtil.memFree(zeros);
        nearest(GL12.GL_TEXTURE_3D);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, previous);
        return texture;
    }

    private static void nearest(int target) {
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }
}
