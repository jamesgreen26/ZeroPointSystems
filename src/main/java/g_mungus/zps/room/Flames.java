package g_mungus.zps.room;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Every loaded flame ({@link Burner}) in a level, by chunk, so each can be seen to every
 * {@link RoomManager#BURN_INTERVAL} ticks as a vent's block entity sees to its gas. Found when a
 * chunk loads and kept up as blocks change, and looked for again in every loaded chunk when the
 * datapacks' flames ({@link Extinguishing}) reload; nothing here is saved.
 */
final class Flames {
    private final Long2ObjectMap<LongSet> byChunk = new Long2ObjectOpenHashMap<>();
    private final LongSet loadedChunks = new LongOpenHashSet();
    /** The {@link Extinguishing#generation} the flames were found under. */
    private int generation = Extinguishing.generation();

    /** A block changed: track it if it is a flame now, forget it if not. */
    void onBlockChanged(BlockPos pos, BlockState newState) {
        long chunk = ChunkPos.asLong(pos);
        if (Burner.of(newState) != null) {
            byChunk.computeIfAbsent(chunk, ignored -> new LongOpenHashSet()).add(pos.asLong());
        } else {
            LongSet flames = byChunk.get(chunk);
            if (flames != null && flames.remove(pos.asLong()) && flames.isEmpty()) {
                byChunk.remove(chunk);
            }
        }
    }

    /** Every flame in a chunk that has just loaded. */
    void onChunkLoaded(LevelChunk chunk) {
        loadedChunks.add(chunk.getPos().toLong());
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        LongSet flames = new LongOpenHashSet();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> Burner.of(state) != null)) {
                continue;
            }
            int minY = chunk.getMinBuildHeight() + i * LevelChunkSection.SECTION_HEIGHT;
            for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
                for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
                    for (int x = 0; x < LevelChunkSection.SECTION_WIDTH; x++) {
                        if (Burner.of(section.getBlockState(x, y, z)) != null) {
                            flames.add(BlockPos.asLong(minX + x, minY + y, minZ + z));
                        }
                    }
                }
            }
        }
        if (flames.isEmpty()) {
            byChunk.remove(chunk.getPos().toLong());
        } else {
            byChunk.put(chunk.getPos().toLong(), flames);
        }
    }

    void onChunkUnloaded(ChunkPos chunk) {
        byChunk.remove(chunk.toLong());
        loadedChunks.remove(chunk.toLong());
    }

    /** If the datapacks' flames have reloaded since they were found, look for them again in every loaded chunk. */
    void refresh(ServerLevel level) {
        if (generation == Extinguishing.generation()) {
            return;
        }
        generation = Extinguishing.generation();
        for (long chunk : loadedChunks.toLongArray()) {
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (loaded != null) {
                onChunkLoaded(loaded);
            } else {
                onChunkUnloaded(new ChunkPos(chunk));
            }
        }
    }

    /** Every tracked flame, copied out so putting one out does not disturb the tracking being read. */
    long[] cells() {
        LongArrayList all = new LongArrayList();
        byChunk.values().forEach(all::addAll);
        return all.toLongArray();
    }

    /** Put out the flame at {@code pos} if it is still one; it then drops out of the tracking. */
    static void extinguish(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Burner burner = Burner.of(state);
        if (burner != null) {
            burner.extinguish(level, pos, state);
        }
    }
}
