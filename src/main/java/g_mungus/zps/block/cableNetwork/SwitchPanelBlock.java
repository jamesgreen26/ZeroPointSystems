package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.SwitchPanelBlockEntity;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchPanelBlock extends PanelBlock implements EntityBlock {

    public static final BooleanProperty POWERED_0 = BooleanProperty.create("powered_0");
    public static final BooleanProperty POWERED_1 = BooleanProperty.create("powered_1");
    public static final BooleanProperty POWERED_2 = BooleanProperty.create("powered_2");
    public static final BooleanProperty POWERED_3 = BooleanProperty.create("powered_3");


    public SwitchPanelBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(POWERED_0, false)
                .setValue(POWERED_1, false)
                .setValue(POWERED_2, false)
                .setValue(POWERED_3, false)
                .setValue(CONNECTED, false)
        );
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        arg.add(FACE, FACING, POWERED_0, POWERED_1, POWERED_2, POWERED_3, CONNECTED);
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.DEFAULT;
    }

    @Override
    public int getTotalChannelCount() {
        return 4;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        return getConnectingPos(state, self).equals(from) ? 4 : 0;
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.toQuad(input.channel());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new SwitchPanelBlockEntity(arg, arg2);
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

        if (quadrant == -1) {
            return InteractionResult.PASS;
        }

        BooleanProperty prop = switch (quadrant) {
            case 0 -> POWERED_0;
            case 1 -> POWERED_1;
            case 2 -> POWERED_2;
            case 3 -> POWERED_3;
            default -> throw new IllegalStateException();
        };

        level.setBlock(
                pos,
                state.cycle(prop),
                Block.UPDATE_ALL
        );

        level.playSound(
                null,
                pos,
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                state.getValue(prop) ? 0.5F : 0.6F
        );

        level.updateNeighborsAt(pos, this);
        updateSignal(level, pos, quadrant + 1); // Quad channels are 1 through 4

        return InteractionResult.CONSUME;
    }

    private static void updateSignal(@NotNull Level level, @NotNull BlockPos pos, int channel) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SwitchPanelBlockEntity switchPanelBlockEntity) {
            switchPanelBlockEntity.updateSignal(level, channel);
        }
    }


}
