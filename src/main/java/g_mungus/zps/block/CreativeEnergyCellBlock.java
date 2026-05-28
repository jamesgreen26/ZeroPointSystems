package g_mungus.zps.block;

import g_mungus.zps.blockentity.CreativeEnergyCellBlockEntity;
import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreativeEnergyCellBlock extends BaseEntityBlock {
    public CreativeEnergyCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.CREATIVE_ENERGY_CELL.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                   @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, be) -> {
            if (be instanceof CreativeEnergyCellBlockEntity) {
                CreativeEnergyCellBlockEntity.tick(level1, pos);
            }
        };
    }
}
