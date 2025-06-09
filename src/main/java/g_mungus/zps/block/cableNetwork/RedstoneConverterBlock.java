package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.RedstoneConverterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedstoneConverterBlock extends TransformerBlock {
    public RedstoneConverterBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.REDSTONE_CONVERTER.get().create(pos, state);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (state.is(ModBlocks.REDSTONE_CONVERTER.get()) && state.getValue(TransformerBlock.FACING).getOpposite() == direction) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getDirectSignal(@NotNull BlockState state, BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneConverterBlockEntity && direction.equals(state.getValue(TransformerBlock.FACING).getOpposite())) {
            return ((RedstoneConverterBlockEntity) blockEntity).getCurrentSignal();
        } else return 0;
    }

    @Override
    public int getSignal(@NotNull BlockState state, BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return getDirectSignal(state, level, pos, direction);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        Direction facing = state.getValue(TransformerBlock.FACING);
        BlockPos sourcePos = pos.offset(facing.getNormal());
        int power = level.getDirectSignal(sourcePos, facing);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneConverterBlockEntity) {
            ((RedstoneConverterBlockEntity) blockEntity).supplySignal(power);
        }
    }
}
