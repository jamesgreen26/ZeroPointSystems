package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import static g_mungus.zps.block.cableNetwork.properties.InsulationType.*;

public class DenseCablesBlock extends CableBlock {

    private static final VoxelShape CORE = Block.box(2, 2, 2, 14, 14, 14);
    private static final VoxelShape NORTH_SHAPE = Block.box(3, 3, 0, 13, 13, 2);
    private static final VoxelShape SOUTH_SHAPE = Block.box(3, 3, 14, 13, 13, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(14, 3, 3, 16, 13, 13);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 3, 3, 2, 13, 13);
    private static final VoxelShape UP_SHAPE = Block.box(3, 14, 3, 13, 16, 13);
    private static final VoxelShape DOWN_SHAPE  = Block.box(3, 0, 3, 13, 2, 13);

    public DenseCablesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        InsulationType insulationType = state.getValue(INSULATION_TYPE);
        if (insulationType.equals(INSULATION) || insulationType.equals(GRATING)) {
            return Shapes.block();
        }

        VoxelShape shape = CORE;

        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_SHAPE);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_SHAPE);
        if (state.getValue(INSULATION_TYPE).equals(CATWALK)) shape = Shapes.or(shape, CATWALK_SHAPE);

        return shape;
    }

    @Override
    public int getTotalChannelCount() {
        return 4;
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.toQuad(input.channel());
    }
}
