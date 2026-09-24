package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Sneak-using a powder snow cauldron with empty hands scoops one layer out as a snowball.
 *
 * <p>Registered as the powder snow cauldron's interaction for the "air" item, which is what an
 * empty hand looks up. Vanilla skips block interaction for a sneaking player who holds anything
 * in either hand, so this only ever fires with both hands empty; a plain click still falls
 * through to the default block interaction.
 */
public final class PowderSnowCauldronScoop {

    private PowderSnowCauldronScoop() {
    }

    public static void register() {
        CauldronInteraction.POWDER_SNOW.put(Items.AIR, PowderSnowCauldronScoop::scoop);
    }

    private static InteractionResult scoop(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!player.isSecondaryUseActive() || !stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            player.setItemInHand(hand, new ItemStack(Items.SNOWBALL));
            player.awardStat(Stats.USE_CAULDRON);
            level.playSound(null, pos, SoundEvents.POWDER_SNOW_BREAK, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
