package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SpaceGratingBlock extends GlassBlock {
    public SpaceGratingBlock(Properties p_49795_) {
        super(p_49795_);
    }

    public void spawnDestroyParticlesPublic(Level level, Player player, BlockPos pos, BlockState state) {
        super.spawnDestroyParticles(level, player, pos, state);
    }
}
