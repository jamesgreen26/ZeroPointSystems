package g_mungus.zps.item;

import g_mungus.zps.block.CatwalkBlock;
import g_mungus.zps.block.cableNetwork.CableBlock;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Predicate;

public class CatwalkBlockItem extends BlockItem {
  private final int placementHelperID;

  public CatwalkBlockItem(Block block, Properties props) {
    super(block, props);
    placementHelperID = PlacementHelpers.register(new CatwalkBlockItem.CatwalkHelper());
  }

  @Override
  public @NotNull InteractionResult useOn(UseOnContext ctx) {
    BlockPos pos = ctx.getClickedPos();
    Direction face = ctx.getClickedFace();
    Level world = ctx.getLevel();
    Player player = ctx.getPlayer();

    BlockState state = world.getBlockState(pos);
    IPlacementHelper helper = PlacementHelpers.get(placementHelperID);
    BlockHitResult ray = new BlockHitResult(ctx.getClickLocation(), face, pos, true);
    if (helper.matchesState(state) && player != null && !player.isShiftKeyDown()) {
      BlockPos targetPos = CatwalkHelper.getTargetPos(player, pos);
      BlockState targetState = world.getBlockState(targetPos);
      if (targetState.getBlock() instanceof CableBlock cableBlock && cableBlock.canBeCatwalked(targetState)) {
        BlockHitResult targetRay = new BlockHitResult(ctx.getClickLocation(), face, targetPos, true);
        return targetState.use(world, player, ctx.getHand(), targetRay);
      }

      PlacementOffset result = helper.getOffset(player, world, state, pos, ray);

      if (result.isSuccessful()) {
        return result.placeInWorld(world, this, player, ctx.getHand(), ray);
      }
    }
    return super.useOn(ctx);
  }

  @MethodsReturnNonnullByDefault
  @ParametersAreNonnullByDefault
  public static class CatwalkHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate () {
      return itemStack -> itemStack.getItem() instanceof CatwalkBlockItem;
    }

    @Override
    public Predicate<BlockState> getStatePredicate () {
      return state -> state.getBlock() instanceof CatwalkBlock
              || state.getBlock() instanceof CableBlock
              && state.hasProperty(CableBlock.CATWALKED)
              && state.getValue(CableBlock.CATWALKED);
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
      BlockPos newPos = getTargetPos(player, pos);

      BlockState targetState = level.getBlockState(newPos);
      if (!targetState.canBeReplaced() && !(targetState.getBlock() instanceof CableBlock cableBlock && cableBlock.canBeCatwalked(targetState)))
        return PlacementOffset.fail();

      return PlacementOffset.success(newPos, offsetState -> offsetState);
    }

    private static BlockPos getTargetPos(Player player, BlockPos pos) {
      return pos.offset(player.getDirection().getNormal());
    }
  }
}
