package g_mungus.zps.blockentity;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public abstract class RideableNetworkTerminal<T extends Entity> extends NetworkTerminal{

    public RideableNetworkTerminal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected final List<T> seats = new ArrayList<>();

    public boolean startRiding(Player player, boolean force, BlockPos blockPos, BlockState state, ServerLevel level) {
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

        return ride;
    }

    abstract EntityType<T> getSeatEntity();
    abstract void registerSeatEntity(T seat);

    public T spawnSeat(BlockPos blockPos, BlockState state, ServerLevel level) {

        double height = 0.5;
        BlockPos newPos = blockPos.relative(state.getValue(HorizontalDirectionalBlock.FACING));

        T entity = getSeatEntity().create(level);
        if (entity != null) {
            registerSeatEntity(entity);

            Vector3dc seatEntityPos = new Vector3d(newPos.getX() + 0.5, (newPos.getY() - 0.5) + height, newPos.getZ() + 0.5);
            entity.moveTo(seatEntityPos.x(), seatEntityPos.y(), seatEntityPos.z());

            entity.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    Vec3.atLowerCornerOf(state.getValue(HorizontalDirectionalBlock.FACING).getNormal().offset(entity.getOnPos()))
            );

            seats.add(entity);

            level.addFreshEntityWithPassengers(entity);
        }
        return entity;
    }

    @Override
    public void setRemoved() {
        if (this.getLevel() != null && !this.getLevel().isClientSide) {
            seats.forEach(Entity::kill);
            seats.clear();
        }
        super.setRemoved();
    }
}
