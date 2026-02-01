package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.GraduatedLeverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GraduatedLeverBlock extends PanelBlock implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public static final VoxelShape NORTH_SHAPE;
    public static final VoxelShape EAST_SHAPE;
    public static final VoxelShape SOUTH_SHAPE;
    public static final VoxelShape WEST_SHAPE;


    static {
        NORTH_SHAPE = Block.box(0, 0, 8, 16, 16, 16);
        SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 8);
        WEST_SHAPE = Block.box(8, 0, 0, 16, 16, 16);
        EAST_SHAPE = Block.box(0, 0, 0, 8, 16, 16);
    }

    public GraduatedLeverBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(POWER, 0)
                .setValue(CONNECTED, false));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        arg.add(FACING, FACE, POWER, CONNECTED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!isFrontFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // World → block-local (0..1)
        Vec3 hitPos = hit.getLocation();
        double x = hitPos.x - pos.getX();
        double y = hitPos.y - pos.getY();
        double z = hitPos.z - pos.getZ();

        int quadrant = getQuadrant(state, x, y, z);
        boolean isTopHalf = quadrant < 2;

        int power = state.getValue(POWER);
        boolean changed = true;
        if (power < 15 && isTopHalf) {
            power++;
        } else if (power > 0 && !isTopHalf) {
            power--;
        } else changed = false;

        float frequency = 0.25f + ((power + 5) / 15f) * 0.5f;

        level.playSound(
                null,
                pos,
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                frequency
        );

        if (changed) {
            level.setBlock(
                    pos,
                    state.setValue(POWER, power),
                    Block.UPDATE_ALL
            );

            level.updateNeighborsAt(pos, this);
            updateSignal(level, pos);
        }

        return InteractionResult.CONSUME;
    }

    private static void updateSignal(@NotNull Level level, @NotNull BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof GraduatedLeverBlockEntity graduatedLeverBlockEntity) {
            graduatedLeverBlockEntity.updateSignal(level, Channels.MAIN);
        }
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.DEFAULT;
    }

    @Override
    public int getTotalChannelCount() {
        return 1;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        return getConnectingPos(state, self).equals(from) ? 1 : 0;
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new GraduatedLeverBlockEntity(arg, arg2);
    }
}
