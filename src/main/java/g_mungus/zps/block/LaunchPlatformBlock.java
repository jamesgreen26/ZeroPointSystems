package g_mungus.zps.block;

import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lifts what is built on it into a ship when it gets a redstone signal from a side or from below. See {@link LaunchPlatformLayout} for which
 * blocks go. Needs Sable or Valkyrien Skies; without either the signal does nothing.
 */
public class LaunchPlatformBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public LaunchPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, isPowered(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                   @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            boolean powered = isPowered(level, pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
                if (powered) {
                    launch(serverLevel, pos);
                }
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    /** From the sides or below only, so nothing built on the platform can set it off. */
    private static boolean isPowered(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction != Direction.UP && level.hasSignal(pos.relative(direction), direction)) return true;
        }
        return false;
    }

    private static void launch(ServerLevel level, BlockPos pos) {
        LaunchPlatformLayout layout = LaunchPlatformLayout.find(level, pos);
        if (layout == null || layout.tooBig()) return;
        Compat.assembleShip(level, layout.volume());
    }
}
