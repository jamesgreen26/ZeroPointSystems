package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.block.CableInsulationBlock;
import g_mungus.zps.block.CatwalkBlock;
import g_mungus.zps.block.SpaceGratingBlock;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;
import g_mungus.zps.block.cableNetwork.core.CableComponentBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.properties.EnumPropertyWithAliases;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static g_mungus.zps.block.cableNetwork.properties.InsulationType.*;

public class CableBlock extends CableComponentBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final EnumPropertyWithAliases<InsulationType> INSULATION_TYPE = EnumPropertyWithAliases.createWithAliases("insulated", InsulationType.class);

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 0, 10, 10, 6);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 10, 10, 10, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(10, 6, 6, 16, 10, 10);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 6, 6, 6, 10, 10);
    private static final VoxelShape UP_SHAPE = Block.box(6, 10, 6, 10, 16, 10);
    private static final VoxelShape DOWN_SHAPE = Block.box(6, 0, 6, 10, 6, 10);
    protected static final VoxelShape CATWALK_SHAPE = Block.box(0, 12, 0, 16, 16, 16);
    private static final double CATWALK_MIN_Y = 12.0D / 16.0D;

    public CableBlock(Properties properties) {
        super(properties);
        BlockState defaultState = this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(INSULATION_TYPE, InsulationType.NONE);
        this.registerDefaultState(defaultState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, INSULATION_TYPE);
    }

    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return !state.getValue(INSULATION_TYPE).equals(INSULATION);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getOcclusionShape(BlockState state, @NotNull BlockGetter p_60579_, @NotNull BlockPos p_60580_) {
        if (state.getValue(INSULATION_TYPE).equals(GRATING)) {
            return Shapes.empty();
        } else {
            return super.getOcclusionShape(state, p_60579_, p_60580_);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        if (state.getValue(INSULATION_TYPE).equals(GRATING)) {
            return 1.0F;
        }
        return super.getShadeBrightness(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (shouldCullMatchingInsulatedFace(state, adjacentState)) {
            return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        InsulationType insulationType = state.getValue(INSULATION_TYPE);
        if (insulationType.equals(INSULATION) || insulationType.equals(GRATING)) {
            return Shapes.block();
        }

        VoxelShape shape = CORE;

        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_SHAPE);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_SHAPE);
        if (state.getValue(INSULATION_TYPE).equals(CATWALK)) shape = Shapes.or(shape, CATWALK_SHAPE);

        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos blockPos, Player arg4, InteractionHand arg5, BlockHitResult arg6) {
        if (state.getValue(INSULATION_TYPE).equals(NONE) && arg4.getItemInHand(arg5).is(ModItems.CABLE_INSULATION.get())) {
            if (!level.isClientSide()) {
                level.setBlock(blockPos, state.setValue(INSULATION_TYPE, INSULATION), 3);
            } else {
                net.minecraft.world.level.block.SoundType soundType = state.getSoundType();
                level.playSound(arg4, blockPos, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            if (!arg4.isCreative()) {
                arg4.getItemInHand(arg5).shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else if (state.getValue(INSULATION_TYPE).equals(NONE) && arg4.getItemInHand(arg5).is(ModItems.CATWALK.get()) && canBeCatwalked(state)) {
            if (!level.isClientSide()) {
                level.setBlock(blockPos, state.setValue(INSULATION_TYPE, CATWALK).setValue(UP, false), 3);
            } else {
                net.minecraft.world.level.block.SoundType soundType = ModBlocks.CATWALK.get().defaultBlockState().getSoundType();
                level.playSound(arg4, blockPos, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            if (!arg4.isCreative()) {
                arg4.getItemInHand(arg5).shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else if (state.getValue(INSULATION_TYPE).equals(NONE) && arg4.getItemInHand(arg5).is(ModItems.SPACE_GRATING_BLOCK.get())) {
            if (!level.isClientSide()) {
                level.setBlock(blockPos, state.setValue(INSULATION_TYPE, GRATING), 3);
            } else {
                net.minecraft.world.level.block.SoundType soundType = state.getSoundType();
                level.playSound(arg4, blockPos, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            if (!arg4.isCreative()) {
                arg4.getItemInHand(arg5).shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else {
            return super.use(state, level, blockPos, arg4, arg5, arg6);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext arg) {
        BlockState blockState = this.defaultBlockState();
        return getNewBlockState(blockState, arg.getLevel(), arg.getClickedPos());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(INSULATION)) {
            level.setBlock(pos, state.setValue(INSULATION_TYPE, NONE), level.isClientSide ? 11 : 3);
            if (!level.isClientSide() && !player.isCreative()) {
                popResource(level, pos, new ItemStack(ModItems.CABLE_INSULATION.get(), 1));
            }
            CableInsulationBlock insulationBlock = (CableInsulationBlock) ModBlocks.CABLE_INSULATION.get();
            insulationBlock.spawnDestroyParticlesPublic(level, player, pos, insulationBlock.defaultBlockState());
            return false;
        } else if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(CATWALK)) {
            level.setBlock(pos, state.setValue(INSULATION_TYPE, NONE), level.isClientSide ? 11 : 3);
            if (!level.isClientSide() && !player.isCreative()) {
                popResource(level, pos, new ItemStack(ModItems.CATWALK.get(), 1));
            }
            CatwalkBlock catwalkBlock = (CatwalkBlock) ModBlocks.CATWALK.get();
            catwalkBlock.spawnDestroyParticlesPublic(level, player, pos, catwalkBlock.defaultBlockState());
            return false;
        } else if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(GRATING)) {
            level.setBlock(pos, state.setValue(INSULATION_TYPE, NONE), level.isClientSide ? 11 : 3);
            if (!level.isClientSide() && !player.isCreative()) {
                popResource(level, pos, new ItemStack(ModItems.SPACE_GRATING_BLOCK.get(), 1));
            }
            SpaceGratingBlock gratingBlock = ModBlocks.SPACE_GRATING_BLOCK.get();
            gratingBlock.spawnDestroyParticlesPublic(level, player, pos, gratingBlock.defaultBlockState());
            return false;
        }
        super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player)
    {
        if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(INSULATION) && !player.isShiftKeyDown()) {
            return new ItemStack(ModItems.CABLE_INSULATION.get(), 1);
        } else if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(GRATING) && !player.isShiftKeyDown()) {
            return new ItemStack(ModItems.SPACE_GRATING_BLOCK.get(), 1);
        } else if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(CATWALK) && !player.isShiftKeyDown()) {
            if (target instanceof BlockHitResult blockHitResult && isTargetingCatwalk(blockHitResult, pos)) {
                return new ItemStack(ModItems.CATWALK.get(), 1);
            }
        } else {
            return getCloneItemStack(level, pos, state);
        }
        return getCloneItemStack(level, pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST,  state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST,  state.getValue(SOUTH));
            case CLOCKWISE_180 -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST,  state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST,  state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST,  state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST,  state.getValue(NORTH));
            default -> state;
        };
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT  -> state  // mirror plane runs N-S; swaps E/W
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(WEST, state.getValue(EAST));
            case FRONT_BACK  -> state  // mirror plane runs E-W; swaps N/S
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(NORTH));
            default -> state;
        };
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.DEFAULT;
    }

    @Override
    public void updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState newState = getNewBlockState(state, level, pos);

        if (!state.equals(newState) && !state.getValue(INSULATION_TYPE).insulatesAllSides()) {
            level.setBlock(pos, newState, 3);
            updateNetwork(pos, level);
        } else if (state.getValue(INSULATION_TYPE).insulatesAllSides()) {
            updateNetwork(pos, level);
        }
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public int getTotalChannelCount() {
        return 1;
    }

    @Override
    public int getChannelCountForConnection(BlockPos self, BlockPos from, Level level) {
        BlockState state = level.getBlockState(self);
        Vec3i n = from.subtract(self);
        Direction direction = Direction.fromDelta(n.getX(), n.getY(), n.getZ());

        if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).equals(CATWALK) && direction == Direction.UP) {
            return 0;
        }

        if (state.hasProperty(INSULATION_TYPE) && state.getValue(INSULATION_TYPE).insulatesAllSides()) {

            boolean shouldConnect = false;

            if (direction == Direction.NORTH) {
                shouldConnect = state.getValue(NORTH);
            } else if (direction == Direction.SOUTH) {
                shouldConnect = state.getValue(SOUTH);
            } else if (direction == Direction.EAST) {
                shouldConnect = state.getValue(EAST);
            } else if (direction == Direction.WEST) {
                shouldConnect = state.getValue(WEST);
            } else if (direction == Direction.UP) {
                shouldConnect = state.getValue(UP);
            } else if (direction == Direction.DOWN) {
                shouldConnect = state.getValue(DOWN);
            }

            if (!shouldConnect) {
                return 0;
            }
        }
        return getTotalChannelCount();
    }

    @Override
    public List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level) {
        BlockPos origin = self.pos();
        return new ArrayList<>(List.of(
                origin.above(),
                origin.below(),
                origin.north(),
                origin.east(),
                origin.south(),
                origin.west()
        ));
    }

    @Override
    public int getNewChannel(BlockPos self, NetworkNode input, Level level) {
        return Channels.MAIN;
    }


    @NotNull
    public BlockState getNewBlockState(BlockState state, Level level, BlockPos pos) {
        boolean north = canConnect(pos, pos.offset(Direction.NORTH.getNormal()), level);
        boolean south = canConnect(pos, pos.offset(Direction.SOUTH.getNormal()), level);
        boolean east = canConnect(pos, pos.offset(Direction.EAST.getNormal()), level);
        boolean west = canConnect(pos, pos.offset(Direction.WEST.getNormal()), level);
        boolean up = !state.getValue(INSULATION_TYPE).equals(CATWALK)
                && canConnect(pos, pos.offset(Direction.UP.getNormal()), level);
        boolean down = canConnect(pos, pos.offset(Direction.DOWN.getNormal()), level);

        return state
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    public boolean canBeCatwalked(BlockState state) {
        return !state.getValue(INSULATION_TYPE).equals(INSULATION) && !state.getValue(INSULATION_TYPE).equals(CATWALK) && !state.getValue(UP);
    }

    private boolean isTargetingCatwalk(BlockHitResult hitResult, BlockPos pos) {
        return hitResult.getLocation().y - pos.getY() >= CATWALK_MIN_Y;
    }

    protected boolean shouldCullMatchingInsulatedFace(BlockState state, BlockState adjacentState) {
        InsulationType insulationType = state.getValue(INSULATION_TYPE);
        if (insulationType == GRATING && adjacentState.is(ModBlocks.SPACE_GRATING_BLOCK.get())) {
            return true;
        }

        if (!(adjacentState.getBlock() instanceof CableBlock)
                || !adjacentState.hasProperty(INSULATION_TYPE)
                || !state.hasProperty(INSULATION_TYPE)) {
            return false;
        }

        return insulationType.insulatesAllSides()
                && insulationType == adjacentState.getValue(INSULATION_TYPE);
    }

}
