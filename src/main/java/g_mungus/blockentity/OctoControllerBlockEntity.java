package g_mungus.blockentity;

import g_mungus.entity.ModEntities;
import g_mungus.entity.OctoMountingEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public class OctoControllerBlockEntity extends BlockEntity {
    public int a = 0;
    public int b = 0;
    public int c = 0;
    public int d = 0;
    public int e = 0;
    public int f = 0;
    public int g = 0;
    public int h = 0;

    private final List<OctoMountingEntity> seats = new ArrayList<>();

    public OctoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCTO_CONTROLLER.get(), pos, state);
    }

    public boolean startRiding(Player player, boolean force, BlockPos blockPos, BlockState state, ServerLevel level) {
        for (OctoMountingEntity octoMountingEntity : seats) {
            if (!octoMountingEntity.isVehicle()) {
                octoMountingEntity.kill();
            }
        }
        seats.clear();

        OctoMountingEntity seat = spawnSeat(blockPos, state, level);
        boolean ride = player.startRiding(seat, force);

        if (ride) {
            seats.add(seat);
        }

        return ride;
    }

    public OctoMountingEntity spawnSeat(BlockPos blockPos, BlockState state, ServerLevel level) {

        double height = 0.5;
        BlockPos newPos = blockPos.relative(state.getValue(HorizontalDirectionalBlock.FACING));

        OctoMountingEntity entity = ModEntities.OCTO_MOUNTING.get().create(level);
        if (entity != null) {
            entity.blockEntity = this;

            Vector3dc seatEntityPos = new Vector3d(newPos.getX() + 0.5, (newPos.getY() - 0.5) + height, newPos.getZ() + 0.5);
            entity.moveTo(seatEntityPos.x(), seatEntityPos.y(), seatEntityPos.z());

            entity.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    Vec3.atLowerCornerOf(state.getValue(HorizontalDirectionalBlock.FACING).getNormal().offset(entity.getOnPos()))
            );

            entity.isController = true;

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
