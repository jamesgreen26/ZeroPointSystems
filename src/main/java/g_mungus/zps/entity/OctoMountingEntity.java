package g_mungus.zps.entity;

import g_mungus.zps.blockentity.OctoControllerBlockEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class OctoMountingEntity extends ControllerSeatEntity {
    public OctoMountingEntity(@NotNull EntityType<OctoMountingEntity> type, @NotNull Level level) {
        super(type, level);
    }

    @Override
    protected boolean isOwnController(BlockEntity blockEntity) {
        return blockEntity instanceof OctoControllerBlockEntity;
    }
}
