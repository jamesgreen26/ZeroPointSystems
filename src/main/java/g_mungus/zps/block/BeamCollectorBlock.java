package g_mungus.zps.block;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import g_mungus.zps.multiblock.ConnectivityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One block of a Beam Collector panel. Blocks sharing a {@link #FACING} join into square panels up to three wide.
 * <p>
 * {@link #SIZE}, {@link #COLUMN} and {@link #ROW} say how wide the panel is and where in it this block sits, in the
 * panel's own frame (see {@link g_mungus.zps.tractor.PanelFrame}): column 0 is the panel's left, row 0 its top. The
 * models are plain cubes that pick the matching tile of the panel-sized texture for each face, with the lamps
 * drawn over them on a tinted overlay whose colour follows {@link #POWER}, the redstone signal reaching the panel
 * (see {@link #lampColor}).
 */
public class BeamCollectorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, BeamCollectorBlockEntity.MAX_WIDTH);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, BeamCollectorBlockEntity.MAX_WIDTH - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, BeamCollectorBlockEntity.MAX_WIDTH - 1);
    /** The redstone signal reaching the panel, 0 to 15, which is what lights the lamps. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /** Lamp overlay colour with no signal and at full signal; the overlay art is greyscale. */
    public static final int LAMP_OFF_COLOR = 0x5B0507;
    public static final int LAMP_ON_COLOR = 0xFF1A09;

    public BeamCollectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SIZE, 1)
                .setValue(COLUMN, 0)
                .setValue(ROW, 0)
                .setValue(POWER, 0));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE, COLUMN, ROW, POWER);
    }

    /** The lamp overlay's tint for a signal of {@code power}: a straight blend from off to on. */
    public static int lampColor(int power) {
        float t = Mth.clamp(power / 15f, 0f, 1f);
        return 0xFF000000 | FastColor.ARGB32.lerp(t, LAMP_OFF_COLOR, LAMP_ON_COLOR);
    }


    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Placed against the side of an existing panel block, a new one takes that block's facing, so a panel can be
     * built out without lining the camera up each time. Otherwise the mouth faces the player, or away while
     * sneaking.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState neighbour = context.getLevel().getBlockState(against);
        if (neighbour.is(this) && neighbour.getValue(FACING).getAxis() != context.getClickedFace().getAxis()) {
            return defaultBlockState().setValue(FACING, neighbour.getValue(FACING));
        }

        Direction looking = context.getNearestLookingDirection();
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? looking
                : looking.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    private static BeamCollectorBlockEntity beamAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BeamCollectorBlockEntity beam ? beam : null;
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                        @NotNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (oldState.is(this) || movedByPiston || level.isClientSide()) {
            return;
        }
        // The block entity does not exist yet at this point; asking for it creates it.
        BeamCollectorBlockEntity beam = beamAt(level, pos);
        if (beam != null) {
            beam.requestConnectivityUpdate();
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            BeamCollectorBlockEntity beam = beamAt(level, pos);
            if (beam != null) {
                // A lone block has nowhere to hand its contents; a panel gives them to the blocks left behind.
                BeamCollectorBlockEntity controller = beam.getControllerBE();
                beam.dropContentsIfStandalone();
                level.removeBlockEntity(pos);
                ConnectivityHandler.splitMulti(beam);
                if (controller != null) {
                    controller.dropSplitLeftovers(pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                   @NotNull Block neighborBlock, @NotNull BlockPos neighborPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        BeamCollectorBlockEntity beam = beamAt(level, pos);
        if (beam != null) {
            beam.markRedstoneStale();
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        // Holding another one means the player is building the panel out.
        if (player.getMainHandItem().is(asItem()) || player.getOffhandItem().is(asItem())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            BeamCollectorBlockEntity beam = beamAt(level, pos);
            if (beam != null && player instanceof ServerPlayer serverPlayer) {
                beam.openMenu(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.BEAM_COLLECTOR.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                           @NotNull BlockState state,
                                                                           @NotNull BlockEntityType<T> type) {
        if (type != ModBlockEntities.BEAM_COLLECTOR.get()) {
            return null;
        }
        return level.isClientSide()
                ? (tickLevel, pos, tickState, blockEntity) -> ((BeamCollectorBlockEntity) blockEntity).clientTick()
                : (tickLevel, pos, tickState, blockEntity) -> ((BeamCollectorBlockEntity) blockEntity).serverTick();
    }
}
