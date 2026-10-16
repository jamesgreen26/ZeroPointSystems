package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TextDisplayBlock extends CableComponentBlock implements EntityBlock {

    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The biggest a merged display gets along either side, in blocks. */
    public static final int MAX_DISPLAY_SIZE = 3;
    /** The size of the merged display this block belongs to, and where in it the block sits. */
    public static final EnumProperty<DisplayLayout> LAYOUT = EnumProperty.create("layout", DisplayLayout.class);


    public TextDisplayBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any().setValue(CONNECTED, false).setValue(FACING, Direction.NORTH).setValue(LAYOUT, DisplayLayout.SINGLE_1x1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        super.createBlockStateDefinition(arg);
        arg.add(CONNECTED, FACING, LAYOUT);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public int getTotalChannelCount() {
        return 1;
    }

    @Override
    public void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState)) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        }
    }

    /** Scheduled by the block entity when the pipe goes blank, to clear the text a little later. */
    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                        @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TextDisplayBlockEntity display) {
            display.tickPendingClear();
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState arg) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState arg, Level arg2, BlockPos arg3) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof TextDisplayBlockEntity it) {
            return Math.min((int) Math.ceil((15 * ((double)it.getDisplayText().length()) / ((double) it.getMaxLength()))), 15);
        }
        return 0;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        BlockPos behind = self.offset(state.getValue(FACING).getOpposite().getNormal());
        if (from.equals(behind)) {
            return 1;
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        if (self.channel() == Channels.MAIN) {
            BlockState state = level.getBlockState(self.pos());
            return List.of(self.pos().offset(state.getValue(FACING).getOpposite().getNormal()));
        }
        return List.of();
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        Direction backDirection = state.getValue(FACING).getOpposite();
        boolean connected = canConnect(pos, pos.offset(backDirection.getNormal()), level);
        return state.setValue(CONNECTED, connected);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos arg, BlockState arg2) {
        return new TextDisplayBlockEntity(arg, arg2);
    }

    /**
     * Using a dye on the display recolours its text, and a glow ink sac makes it
     * glow (an ink sac takes the glow off again), the same way they work on a
     * vanilla sign (see {@code SignBlock#useItemOn}). Every block of a merged
     * multi-block display takes the change so the text isn't left half-done.
     */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Predicate<TextDisplayBlockEntity> apply;
        SoundEvent sound;
        if (stack.getItem() instanceof DyeItem dye) {
            apply = display -> display.setTextColor(dye.getDyeColor());
            sound = SoundEvents.DYE_USE;
        } else if (stack.is(Items.GLOW_INK_SAC)) {
            apply = display -> display.setGlowingText(true);
            sound = SoundEvents.GLOW_INK_SAC_USE;
        } else if (stack.is(Items.INK_SAC)) {
            apply = display -> display.setGlowingText(false);
            sound = SoundEvents.INK_SAC_USE;
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        boolean changed = false;
        for (BlockPos displayPos : getDisplayPositions(state, pos)) {
            if (level.getBlockEntity(displayPos) instanceof TextDisplayBlockEntity display) {
                changed |= apply.test(display);
            }
        }
        if (!changed) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Shift-using the display with an empty hand cycles its font size between the
     * default and 2x. Like dyes, it applies to every block of a merged display.
     */
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof TextDisplayBlockEntity clicked)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean large = !clicked.isLargeText();
        for (BlockPos displayPos : getDisplayPositions(state, pos)) {
            if (level.getBlockEntity(displayPos) instanceof TextDisplayBlockEntity display) {
                display.setLargeText(large);
            }
        }
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, large ? 0.6F : 0.5F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        return InteractionResult.CONSUME;
    }

    /** All block positions that make up the (possibly multi-block) display containing {@code pos}. */
    public List<BlockPos> getDisplayPositions(BlockState state, BlockPos pos) {
        return DisplayGroup.of(state, pos).cells();
    }

    // -------------------------------------------------------------------------
    // Merging
    //
    // Displays facing the same way merge into rectangles of up to
    // MAX_DISPLAY_SIZE x MAX_DISPLAY_SIZE. A placed block joins the biggest
    // rectangle around it that is full of displays and only swallows existing
    // displays whole, so building never splits a display that is already up.
    // Breaking a block releases the rest of its display, and each released block
    // merges again the same way.
    // -------------------------------------------------------------------------

    @Override
    public void onPlace(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(newState, level, pos, oldState, moved);
        if (level.isClientSide) {
            return;
        }
        boolean turned = oldState.is(this) && oldState.getValue(FACING) != newState.getValue(FACING);
        if (oldState.is(this) && !turned) {
            // A state change from merging, or the connection flag.
            return;
        }
        if (turned) {
            release(level, DisplayGroup.of(oldState, pos), pos);
        }
        merge(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !newState.is(this)) {
            release(level, DisplayGroup.of(state, pos), pos);
        }
    }

    /** Splits {@code group} into single blocks, minus {@code gone}, and merges each one again. */
    private void release(Level level, DisplayGroup group, BlockPos gone) {
        List<BlockPos> released = new ArrayList<>();
        for (BlockPos cell : group.cells()) {
            BlockState cellState = level.getBlockState(cell);
            if (cell.equals(gone) || !isMemberOf(cellState, group)) {
                continue;
            }
            setGroup(level, cell, cellState, 1, 1, 0, 0);
            released.add(cell);
        }
        // Reading order, so the result does not depend on which block went.
        for (BlockPos cell : released) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.is(this) && cellState.getValue(LAYOUT) == DisplayLayout.SINGLE_1x1) {
                merge(level, cell);
            }
        }
    }

    /**
     * Joins the block at {@code pos} into the biggest rectangle of displays around it,
     * preferring wider rectangles on a tie since text runs sideways.
     */
    private void merge(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        DisplayGroup best = new DisplayGroup(pos, facing, 1, 1);
        for (int width = MAX_DISPLAY_SIZE; width >= 1; width--) {
            for (int height = MAX_DISPLAY_SIZE; height >= 1; height--) {
                if (width * height <= best.width() * best.height()) {
                    continue;
                }
                DisplayGroup found = findRectangle(level, pos, facing, width, height);
                if (found != null) {
                    best = found;
                }
            }
        }
        for (int row = 0; row < best.height(); row++) {
            for (int column = 0; column < best.width(); column++) {
                BlockPos cell = best.cell(column, row);
                setGroup(level, cell, level.getBlockState(cell), best.width(), best.height(), column, row);
            }
        }
    }

    /** The first {@code width x height} rectangle holding {@code pos} that can become one display. */
    private @Nullable DisplayGroup findRectangle(Level level, BlockPos pos, Direction facing, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                DisplayGroup candidate = new DisplayGroup(pos, facing, width, height, column, row);
                if (canBecomeDisplay(level, candidate, pos)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Every cell of {@code candidate} is a display facing its way, and every display
     * it touches lies wholly inside it. {@code joining} is the block being merged,
     * whose own current group does not count.
     */
    private boolean canBecomeDisplay(Level level, DisplayGroup candidate, BlockPos joining) {
        for (BlockPos cell : candidate.cells()) {
            BlockState cellState = level.getBlockState(cell);
            if (!cellState.is(this) || cellState.getValue(FACING) != candidate.facing()) {
                return false;
            }
            if (!cell.equals(joining) && !candidate.contains(DisplayGroup.of(cellState, cell))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMemberOf(BlockState state, DisplayGroup group) {
        return state.is(this) && state.getValue(FACING) == group.facing()
                && state.getValue(LAYOUT).width == group.width() && state.getValue(LAYOUT).height == group.height();
    }

    private void setGroup(Level level, BlockPos pos, BlockState state, int width, int height, int column, int row) {
        BlockState grouped = state.setValue(LAYOUT, DisplayLayout.of(width, height, column, row));
        if (grouped != state) {
            level.setBlock(pos, grouped, Block.UPDATE_CLIENTS);
        }
    }
}
