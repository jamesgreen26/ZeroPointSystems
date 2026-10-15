package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.entity.DodecaMountingEntity;
import g_mungus.zps.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DodecaControllerBlockEntity extends ControllerBlockEntity<DodecaMountingEntity> {

    public DodecaControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DODECA_CONTROLLER.get(), pos, state);
    }

    @Override
    protected int firstChannel() {
        return Channels.DOD_A;
    }

    @Override
    public int channelCount() {
        return 12;
    }

    @Override
    EntityType<DodecaMountingEntity> getSeatEntity() {
        return ModEntities.DODECA_MOUNTING.get();
    }
}
