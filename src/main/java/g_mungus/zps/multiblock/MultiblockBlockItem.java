package g_mungus.zps.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block item for {@link MultiblockPart} blocks. Placing one on the top or bottom of an existing vertical
 * structure fills the whole new layer from the stack in one go (unless sneaking), and structure bookkeeping is
 * stripped from any block entity data carried by the item so a picked block never remembers a stale controller.
 * <p>
 * Adapted from Create's {@code FluidTankItem} (MIT).
 */
public class MultiblockBlockItem extends BlockItem {
    /** Persistent-data flag set on the player while a layer is being batch-placed; blocks may quieten sounds. */
    public static final String SILENCE_PLACE_SOUND = "zps:SilencePlaceSound";

    public MultiblockBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** True while {@code entity} is batch-placing a layer, so the block can play a softer placement sound. */
    public static boolean isPlacementSilenced(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().contains(SILENCE_PLACE_SOUND);
    }

    @Override
    public @NotNull InteractionResult place(@NotNull BlockPlaceContext ctx) {
        InteractionResult initialResult = super.place(ctx);
        if (!initialResult.consumesAction()) {
            return initialResult;
        }
        tryMultiPlace(ctx);
        return initialResult;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(@NotNull BlockPos pos, @NotNull Level level, @Nullable Player player,
                                                 @NotNull ItemStack stack, @NotNull BlockState state) {
        CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null) {
            CompoundTag nbt = blockEntityData.copyTag();
            for (String key : MultiblockBlockEntity.STRUCTURE_TAGS) {
                nbt.remove(key);
            }
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    private void tryMultiPlace(BlockPlaceContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isVertical()) {
            return;
        }
        ItemStack stack = ctx.getItemInHand();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos placedOnPos = pos.relative(face.getOpposite());
        BlockState placedOnState = level.getBlockState(placedOnPos);
        if (!placedOnState.is(getBlock())) {
            return;
        }

        BlockEntity placedOn = level.getBlockEntity(placedOnPos);
        if (!(placedOn instanceof MultiblockPart part)) {
            return;
        }
        BlockEntity controllerBE = part.getControllerBE();
        if (!(controllerBE instanceof MultiblockPart controller)) {
            return;
        }
        if (controller.getMainConnectionAxis() != Direction.Axis.Y) {
            return;
        }

        int width = controller.getWidth();
        if (width == 1) {
            return;
        }

        BlockPos startPos = face == Direction.DOWN
                ? controllerBE.getBlockPos().below()
                : controllerBE.getBlockPos().above(controller.getHeight());
        if (startPos.getY() != pos.getY()) {
            return;
        }

        int toPlace = 0;
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
                BlockState blockState = level.getBlockState(offsetPos);
                if (blockState.is(getBlock())) {
                    continue;
                }
                if (!blockState.canBeReplaced()) {
                    return;
                }
                toPlace++;
            }
        }

        if (!player.isCreative() && stack.getCount() < toPlace) {
            return;
        }

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
                BlockState blockState = level.getBlockState(offsetPos);
                if (blockState.is(getBlock())) {
                    continue;
                }
                BlockPlaceContext context = BlockPlaceContext.at(ctx, offsetPos, face);
                player.getPersistentData().putBoolean(SILENCE_PLACE_SOUND, true);
                try {
                    super.place(context);
                } finally {
                    player.getPersistentData().remove(SILENCE_PLACE_SOUND);
                }
            }
        }
    }
}
