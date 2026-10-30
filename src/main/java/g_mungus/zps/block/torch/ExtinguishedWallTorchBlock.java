package g_mungus.zps.block.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** {@link ExtinguishedTorchBlock} on a wall. */
public class ExtinguishedWallTorchBlock extends WallTorchBlock {
    private final Supplier<? extends Block> lit;

    public ExtinguishedWallTorchBlock(Supplier<? extends Block> lit, Properties properties) {
        super(ParticleTypes.SMOKE, properties);
        this.lit = lit;
    }

    /** The wall torch this is, lit. */
    public Block lit() {
        return lit.get();
    }

    /** Picked, it is the torch it was: it drops one too. */
    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(lit());
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
                                                       @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        return ExtinguishedTorchBlock.relight(stack, level, pos, lit().withPropertiesOf(state), player, hand);
    }
}
