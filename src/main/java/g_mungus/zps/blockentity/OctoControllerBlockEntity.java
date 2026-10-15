package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.entity.OctoMountingEntity;
import g_mungus.zps.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

public class OctoControllerBlockEntity extends ControllerBlockEntity<OctoMountingEntity> {

    public OctoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCTO_CONTROLLER.get(), pos, state);
    }

    @Override
    protected int firstChannel() {
        return Channels.OCT_A;
    }

    @Override
    public int channelCount() {
        return 8;
    }

    @Override
    EntityType<OctoMountingEntity> getSeatEntity() {
        return ModEntities.OCTO_MOUNTING.get();
    }
}
