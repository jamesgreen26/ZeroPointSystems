package g_mungus.block.cableNetwork;

import g_mungus.ZPSMod;
import g_mungus.block.ModBlocks;
import g_mungus.block.cableNetwork.core.CableNetworkComponent;
import g_mungus.block.cableNetwork.core.Channels;
import g_mungus.block.cableNetwork.core.NetworkNode;
import g_mungus.blockentity.NetworkTerminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DenseCableSeparatorBlock extends Block implements CableNetworkComponent {

    public static final DirectionProperty FACING = DirectionProperty.create("facing");
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");


    public DenseCableSeparatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROTATION, 0)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
        builder.add(ROTATION);
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult arg6) {
        if (player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) {
            level.setBlock(pos, state.setValue(ROTATION, (state.getValue(ROTATION) + 1) % 4), Block.UPDATE_CLIENTS);

            Block block = state.getBlock();

            if (block instanceof DenseCableSeparatorBlock) {
                ((DenseCableSeparatorBlock) block).updateNetwork(pos, level);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return getNewBlockState(
                defaultBlockState().setValue(FACING, facing),
                context.getLevel(),
                context.getClickedPos()
        );
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updateConnections(state, level, pos);
        }
    }

    private void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState)) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        }
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public int getTotalChannelCount() {
        return 4;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        if (state.hasProperty(FACING)) {
            if (from.equals(self.offset(state.getValue(FACING).getNormal()))) {
                return 4;
            } else if (from.equals(self.offset(state.getValue(FACING).getOpposite().getNormal()))) {
                return 0;
            } else {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockState state = level.getBlockState(self.pos());
        ZPSMod.LOGGER.info("block: {}", state.getBlock().getName());
        return List.of(
                self.pos().offset(state.getValue(FACING).getNormal()),
                getNeighborPosForChannel(self.channel(), self.pos(), state)
        );
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        BlockState state = level.getBlockState(self);
        if (input.channel() == Channels.MAIN) {
            return getChannelForNeighborPos(self, input.pos(), state);
        } else {
            return Channels.toQuad(input.channel());
        }
    }

    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean north = canConnect(pos, pos.offset(Direction.NORTH.getNormal()), level);
        boolean south = canConnect(pos, pos.offset(Direction.SOUTH.getNormal()), level);
        boolean east = canConnect(pos, pos.offset(Direction.EAST.getNormal()), level);
        boolean west = canConnect(pos, pos.offset(Direction.WEST.getNormal()), level);
        boolean up = canConnect(pos, pos.offset(Direction.UP.getNormal()), level);
        boolean down = canConnect(pos, pos.offset(Direction.DOWN.getNormal()), level);

        return state
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    public BlockPos getNeighborPosForChannel(int channel, BlockPos self, BlockState state) {
        if (!state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) return self;
        Direction facing = state.getValue(FACING);
        int rotation = state.getValue(ROTATION);

        Vec3i c0;
        if (!facing.getAxis().equals(Direction.Axis.Y)) {
            c0 = Direction.UP.getNormal();
        } else if (facing.equals(Direction.UP)) {
            c0 = Direction.SOUTH.getNormal();
        } else {
            c0 = Direction.NORTH.getNormal();
        }

        int index = (8 + channel - rotation - 1) % 4;

        Vec3i selected = switch (index) {
            default -> c0;
            case 1 -> facing.getNormal().cross(c0).multiply(-1);
            case 2 -> c0.multiply(-1);
            case 3 -> facing.getNormal().cross(c0);
        };

        ZPSMod.LOGGER.info("Position: {}, channel: {}", self.offset(selected).toShortString(), channel);

        return self.offset(selected);
    }

    public int getChannelForNeighborPos(BlockPos self, BlockPos neighbor, BlockState state) {
        if (!state.is(ModBlocks.DENSE_CABLE_SEPARATOR.get())) {
            ZPSMod.LOGGER.info("not a cable separator: {} at {}", state.getBlock().getName(), self);
            return -1;
        }
        Direction facing = state.getValue(FACING);
        int rotation = state.getValue(ROTATION);

        Vec3i c0;
        if (!facing.getAxis().equals(Direction.Axis.Y)) {
            c0 = Direction.UP.getNormal();
        } else if (facing.equals(Direction.UP)) {
            c0 = Direction.SOUTH.getNormal();
        } else {
            c0 = Direction.NORTH.getNormal();
        }

        Vec3i offset = neighbor.subtract(self);

        // Check if the offset matches any of the possible channel positions
        if (offset.equals(c0)) {
            return 1 + ((rotation) % 4);
        } else if (offset.equals(facing.getNormal().cross(c0).multiply(-1))) {
            return 1 + ((rotation + 1) % 4);
        } else if (offset.equals(c0.multiply(-1))) {
            return 1 + ((rotation + 2) % 4);
        } else if (offset.equals(facing.getNormal().cross(c0))) {
            return 1 + ((rotation + 3) % 4);
        }

        ZPSMod.LOGGER.info("none matched at {}", self);

        return -1;
    }
}