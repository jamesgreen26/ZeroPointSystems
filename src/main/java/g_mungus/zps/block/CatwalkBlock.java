package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class CatwalkBlock extends Block {
    private static final VoxelShape BOX_COLLIDER;
    public CatwalkBlock(Properties arg) {
        super(arg);
    }

    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(@NotNull BlockState arg, @NotNull BlockGetter arg2, @NotNull BlockPos arg3, @NotNull CollisionContext arg4) {
        return BOX_COLLIDER;
    }

    static {
        BOX_COLLIDER = Block.box(0F, 12.0F, 0.0F, 16.0F, 16.0F, 16.0F);
    }
}
