package g_mungus.zps.item;

import g_mungus.zps.block.cableNetwork.light_pipe.RadioAntenna;
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

public class RadioAntennaBlockItem extends BlockItem {
    private static final int MAX_EXTEND_HEIGHT = 5;
    private final int placementHelperID;

    public RadioAntennaBlockItem(Block block, Properties props) {
        super(block, props);
        placementHelperID = PlacementHelpers.register(new RadioAntennaHelper());
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
            PlacementOffset result = helper.getOffset(player, world, state, pos, ray);

            if (result.isSuccessful()) {
                return result.placeInWorld(world, this, player, ctx.getHand(), ray).result();
            }
        }
        return super.useOn(ctx);
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static class RadioAntennaHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return itemStack -> itemStack.getItem() instanceof RadioAntennaBlockItem;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof RadioAntenna;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
            for (int i = 1; i <= MAX_EXTEND_HEIGHT; i++) {
                BlockPos newPos = pos.above(i);
                BlockState newState = level.getBlockState(newPos);

                if (newState.getBlock() instanceof RadioAntenna) {
                    continue;
                }

                if (newState.canBeReplaced()) {
                    return PlacementOffset.success(newPos, offsetState -> offsetState);
                }

                return PlacementOffset.fail();
            }

            return PlacementOffset.fail();
        }
    }
}
