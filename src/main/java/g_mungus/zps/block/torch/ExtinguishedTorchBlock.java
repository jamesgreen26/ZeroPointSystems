package g_mungus.zps.block.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A torch that went out for want of air in a sealed room ({@code room.Burner}). It gives no light
 * and no flame, flint and steel or a fire charge lights it again, and broken it drops the torch it
 * was. There is no item of its own.
 */
public class ExtinguishedTorchBlock extends TorchBlock {
    private final Supplier<? extends Block> lit;

    public ExtinguishedTorchBlock(Supplier<? extends Block> lit, Properties properties) {
        super(properties, ParticleTypes.SMOKE);
        this.lit = lit;
    }

    /** The torch this is, lit. */
    public Block lit() {
        return lit.get();
    }

    /** Picked, it is the torch it was: it drops one too. */
    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(lit());
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        return relight(stack, level, pos, lit().withPropertiesOf(state), player, hand);
    }

    /**
     * Light an extinguished torch or lantern at {@code pos} into {@code litState} if {@code stack} is
     * flint and steel or a fire charge, using it up as lighting a fire would.
     */
    static InteractionResult relight(ItemStack stack, Level level, BlockPos pos, BlockState litState,
                                         Player player, InteractionHand hand) {
        boolean flint = stack.is(Items.FLINT_AND_STEEL);
        if (!flint && !stack.is(Items.FIRE_CHARGE)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.playSound(null, pos, flint ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS,
                    1.0f, level.getRandom().nextFloat() * 0.4f + 0.8f);
            level.setBlockAndUpdate(pos, litState);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            if (flint) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            } else {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
