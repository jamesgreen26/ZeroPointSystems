package g_mungus.zps.block.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A lantern that went out for want of breathable gas ({@code room.Burner}), as an
 * {@link ExtinguishedTorchBlock} is a torch: no light, lit again with flint and steel or a fire
 * charge, and broken it drops the lantern it was.
 */
public class ExtinguishedLanternBlock extends LanternBlock {
    private final Supplier<? extends Block> lit;

    public ExtinguishedLanternBlock(Supplier<? extends Block> lit, Properties properties) {
        super(properties);
        this.lit = lit;
    }

    /** The lantern this is, lit. */
    public Block lit() {
        return lit.get();
    }

    /** Picked, it is the lantern it was: it drops one too. */
    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(lit());
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
                                                       @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        return ExtinguishedTorchBlock.relight(stack, level, pos, lit().withPropertiesOf(state), player, hand);
    }
}
