package g_mungus.zps.gas.api;

import g_mungus.zps.reactor.ReactorManager;
import g_mungus.zps.room.RoomManager;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * Carries ZPS's sealed spaces — fusion reactors and rooms — along with their blocks when those
 * blocks move to another level wholesale: a ship or sublevel changing dimension. ZPS keeps them per
 * level, by position, so without this they stay behind where the blocks were and the blocks arrive
 * as empty shells.
 *
 * <p>Two steps around the move, on the server thread, in the same tick:
 * <pre>{@code
 * GridTransfer.Cargo cargo = GridTransfer.detach(from, GridTransfer.Region.chunks(claim::contains));
 * // ... move the blocks to `to` ...
 * cargo.attach(to, BlockPos.ZERO);
 * }</pre>
 * {@link #detach} goes first, while the blocks are still in {@code from}: it reads the reactors'
 * chambers and ports there, and once it is done nothing in the region can fail as the blocks are
 * taken away — a lit reactor leaves as quietly as a cold one. {@link Cargo#attach} goes once the
 * blocks are in {@code to}. If the move doesn't happen after all, attach the cargo back to
 * {@code from}.
 *
 * <p>Only what lies wholly in the region goes. A ship's grid is closed off from the world, so
 * nothing on it should straddle the edge; anything that does is left as it is.
 */
public final class GridTransfer {

    private GridTransfer() {
    }

    /** Take every reactor and room in {@code region} out of {@code from}, ready for {@link Cargo#attach}. */
    public static Cargo detach(ServerLevel from, Region region) {
        LongPredicate cells = pos -> region.contains(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        return new Cargo(from, ReactorManager.get(from).detach(from, cells), RoomManager.get(from).detach(cells));
    }

    /** Where the blocks being moved are. */
    @FunctionalInterface
    public interface Region {

        boolean contains(int x, int y, int z);

        /**
         * The chunks {@code test} accepts, at every height: a Valkyrien Skies ship's chunk claim,
         * say. Each chunk is asked about once and the answer kept, so make a new region per move.
         */
        static Region chunks(Predicate<ChunkPos> test) {
            Long2BooleanOpenHashMap answers = new Long2BooleanOpenHashMap();
            return (x, y, z) -> {
                long key = ChunkPos.asLong(x >> 4, z >> 4);
                if (answers.containsKey(key)) {
                    return answers.get(key);
                }
                boolean answer = test.test(new ChunkPos(x >> 4, z >> 4));
                answers.put(key, answer);
                return answer;
            };
        }

        /** The blocks in {@code box}, inclusive. */
        static Region box(BoundingBox box) {
            return box::isInside;
        }
    }

    /** Reactors and rooms taken out of one level, on their way to another. Attach it once. */
    public static final class Cargo {

        private final ServerLevel from;
        private final List<CompoundTag> reactors;
        private final List<CompoundTag> rooms;
        private boolean attached;

        private Cargo(ServerLevel from, List<CompoundTag> reactors, List<CompoundTag> rooms) {
            this.from = from;
            this.reactors = reactors;
            this.rooms = rooms;
        }

        /**
         * Put everything into {@code to}, each position moved by {@code offset}: zero if the blocks
         * kept their coordinates, as a Valkyrien Skies shipyard does. The blocks must be there
         * already.
         *
         * @throws IllegalStateException if this cargo has been attached before
         */
        public void attach(ServerLevel to, BlockPos offset) {
            if (attached) {
                throw new IllegalStateException("Cargo detached from " + from.dimension().location()
                        + " has been attached already");
            }
            attached = true;
            if (!reactors.isEmpty()) {
                ReactorManager.get(to).attach(to, reactors, offset);
            }
            if (!rooms.isEmpty()) {
                RoomManager.get(to).attach(rooms, offset);
            }
        }

        public ServerLevel from() {
            return from;
        }

        public int reactorCount() {
            return reactors.size();
        }

        public int roomCount() {
            return rooms.size();
        }

        public boolean isEmpty() {
            return reactors.isEmpty() && rooms.isEmpty();
        }
    }
}
