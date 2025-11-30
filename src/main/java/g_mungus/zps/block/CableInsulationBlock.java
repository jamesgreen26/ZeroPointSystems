package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CableInsulationBlock extends Block {

    public static BooleanProperty NORTH = BooleanProperty.create("north");
    public static BooleanProperty SOUTH = BooleanProperty.create("south");
    public static BooleanProperty EAST = BooleanProperty.create("east");
    public static BooleanProperty WEST = BooleanProperty.create("west");
    public static BooleanProperty UP = BooleanProperty.create("up");
    public static BooleanProperty DOWN = BooleanProperty.create("down");

    public CableInsulationBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext arg) {
        BlockState blockState = this.defaultBlockState();
        return getNewBlockState(blockState, arg.getLevel(), arg.getClickedPos());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            BlockState newState = getNewBlockState(state, level, pos);

            if (!state.equals(newState)) {
                level.setBlock(pos, newState, 3);
            }
        }
    }

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean north = canConnect(pos.offset(Direction.NORTH.getNormal()), level);
        boolean south = canConnect(pos.offset(Direction.SOUTH.getNormal()), level);
        boolean east = canConnect(pos.offset(Direction.EAST.getNormal()), level);
        boolean west = canConnect(pos.offset(Direction.WEST.getNormal()), level);
        boolean up = canConnect(pos.offset(Direction.UP.getNormal()), level);
        boolean down = canConnect(pos.offset(Direction.DOWN.getNormal()), level);

        return state
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    private boolean canConnect(BlockPos connectTo, Level level) {
        return level.getBlockState(connectTo).is(ModBlocks.CABLE_INSULATION.get());
    }
}
