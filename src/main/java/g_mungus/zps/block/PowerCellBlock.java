package g_mungus.zps.block;

import com.mojang.serialization.MapCodec;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.multiblock.ConnectivityHandler;
import g_mungus.zps.multiblock.MultiblockBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A cell of a Power Cell structure. Besides the fill {@link #LEVEL}, the state records where the block sits in
 * its structure ({@link #TOP}/{@link #BOTTOM} layers, and which horizontal sides face outwards) so the model can
 * draw the end plates and inset walls only on the structure's outer faces.
 */
public class PowerCellBlock extends BaseEntityBlock {
    private static final MapCodec<PowerCellBlock> CODEC = simpleCodec(PowerCellBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 9);
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    /** Cells are quieter when a whole layer is placed at once. */
    private static final SoundType SILENCED_METAL = new DeferredSoundType(0.1F, 1.5F,
            () -> SoundEvents.METAL_BREAK, () -> SoundEvents.METAL_STEP, () -> SoundEvents.METAL_PLACE,
            () -> SoundEvents.METAL_HIT, () -> SoundEvents.METAL_FALL);

    public PowerCellBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LEVEL, 0)
                .setValue(TOP, true)
                .setValue(BOTTOM, true)
                .setValue(NORTH, true)
                .setValue(EAST, true)
                .setValue(SOUTH, true)
                .setValue(WEST, true));
    }

    public static boolean isCell(BlockState state) {
        return state.getBlock() instanceof PowerCellBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(LEVEL, TOP, BOTTOM, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        PowerCellBlockEntity cell = cellAt(level, pos);
        return cell == null ? 0 : cell.getComparatorOutputSignal();
    }

    @Nullable
    private static PowerCellBlockEntity cellAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PowerCellBlockEntity cell ? cell : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.POWER_CELL.get().create(pos, state);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                        @NotNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (oldState.is(this) || movedByPiston || level.isClientSide()) {
            return;
        }
        // The block entity does not exist yet at this point; asking for it creates it.
        PowerCellBlockEntity cell = cellAt(level, pos);
        if (cell != null) {
            cell.requestConnectivityUpdate();
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            PowerCellBlockEntity cell = cellAt(level, pos);
            if (cell != null && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(cell.createMenuProvider(pos), pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            PowerCellBlockEntity cell = cellAt(level, pos);
            if (cell != null) {
                cell.dropContents();
                level.removeBlockEntity(pos);
                ConnectivityHandler.splitMulti(cell);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @NotNull SoundType getSoundType(@NotNull BlockState state, @NotNull LevelReader level,
                                           @NotNull BlockPos pos, @Nullable Entity entity) {
        if (MultiblockBlockItem.isPlacementSilenced(entity)) {
            return SILENCED_METAL;
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos, state1, be) -> {
            if (be instanceof PowerCellBlockEntity powerCellBlockEntity) {
                powerCellBlockEntity.serverTick();
            }
        };
    }
}
