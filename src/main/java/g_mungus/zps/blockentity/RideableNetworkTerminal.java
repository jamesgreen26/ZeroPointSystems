package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public abstract class RideableNetworkTerminal<T extends Entity> extends NetworkTerminalImpl {

    public RideableNetworkTerminal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected final List<T> seats = new ArrayList<>();

    public void startRiding(Player player, boolean force, BlockPos blockPos, BlockState state, ServerLevel level) {
        for (T seat : seats) {
            if (!seat.isVehicle()) {
                seat.kill();
            }
        }
        seats.clear();

        T seat = spawnSeat(blockPos, state, level);
        boolean ride = player.startRiding(seat, force);

        if (ride) {
            seats.add(seat);
        }

    }

    abstract EntityType<T> getSeatEntity();
    abstract void registerSeatEntity(T seat);

    /**
     * Spawns the seat centred inside the block itself, so it stays in the block's chunk and can only
     * belong to this block; the seat puts its rider wherever they should stand.
     */
    public T spawnSeat(BlockPos blockPos, BlockState state, ServerLevel level) {
        T entity = getSeatEntity().create(level);
        if (entity != null) {
            registerSeatEntity(entity);

            Vec3 centre = Vec3.atCenterOf(blockPos);
            entity.moveTo(centre.x, centre.y - entity.getBbHeight() / 2, centre.z,
                    state.getValue(HorizontalDirectionalBlock.FACING).toYRot(), 0.0f);

            seats.add(entity);

            level.addFreshEntityWithPassengers(entity);
        }
        return entity;
    }
}
