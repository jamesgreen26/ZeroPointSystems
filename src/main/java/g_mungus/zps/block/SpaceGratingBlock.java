package g_mungus.zps.block;

import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class SpaceGratingBlock extends Block {
    public SpaceGratingBlock(Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public boolean skipRendering(@NotNull BlockState state, BlockState adjacentState, @NotNull Direction direction) {
        if (adjacentState.getBlock() instanceof CableBlock
                && adjacentState.hasProperty(CableBlock.INSULATION_TYPE)
                && adjacentState.getValue(CableBlock.INSULATION_TYPE) == InsulationType.GRATING
            || adjacentState.getBlock() instanceof SpaceGratingBlock
        ) {

            return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }

    public void spawnDestroyParticlesPublic(Level level, Player player, BlockPos pos, BlockState state) {
        super.spawnDestroyParticles(level, player, pos, state);
    }
}
