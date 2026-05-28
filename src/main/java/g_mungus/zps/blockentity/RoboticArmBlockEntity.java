package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RoboticArmBlockEntity extends NetworkTerminalImpl {
    public static final int MOVE_TIME_TICKS = 16;
    public static final int MAX_DISTANCE_BLOCKS = 4;

    private boolean moving;
    private BlockPos handBlockPos;
    private BlockPos moveStartBlockPos;
    private BlockPos moveTargetBlockPos;
    private long moveStartTick;

    public RoboticArmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROBOTIC_ARM.get(), pos, state);
        handBlockPos = pos.above();
        moveStartBlockPos = pos;
        moveTargetBlockPos = pos;
    }

    public void tickServer() {
        if (level == null) return;

        long gameTime = level.getGameTime();

        if (moving && gameTime - moveStartTick >= MOVE_TIME_TICKS) {
            moving = false;
            handBlockPos = moveTargetBlockPos;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        if (gameTime % 20L == 0L) {
            moveHandTo(pickRandomInRange());
        }
    }

    public boolean isMoving() {
        return moving;
    }

    public BlockPos getHandBlockPos() {
        return handBlockPos;
    }

    public BlockPos getMoveStartBlockPos() {
        return moveStartBlockPos;
    }

    public BlockPos getMoveTargetBlockPos() {
        return moveTargetBlockPos;
    }

    public long getMoveStartTick() {
        return moveStartTick;
    }

    public boolean moveHandTo(BlockPos newBlockPos) {
        if (level == null || moving) return false;
        if (newBlockPos.distSqr(worldPosition) > (double) (MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS)) return false;

        moveStartBlockPos = handBlockPos;
        moveTargetBlockPos = newBlockPos;
        moveStartTick = level.getGameTime();
        moving = true;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private BlockPos pickRandomInRange() {
        if (level == null) return handBlockPos;

        for (int attempt = 0; attempt < 8; attempt++) {
            int x = worldPosition.getX() + level.random.nextInt((MAX_DISTANCE_BLOCKS * 2) + 1) - MAX_DISTANCE_BLOCKS;
            int y = worldPosition.getY() + level.random.nextInt((MAX_DISTANCE_BLOCKS * 2) + 1) - MAX_DISTANCE_BLOCKS;
            int z = worldPosition.getZ() + level.random.nextInt((MAX_DISTANCE_BLOCKS * 2) + 1) - MAX_DISTANCE_BLOCKS;
            BlockPos candidate = new BlockPos(x, y, z);
            if (candidate.distSqr(worldPosition) <= (double) (MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS) && !candidate.equals(worldPosition)) {
                return candidate;
            }
        }

        return handBlockPos;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeArmState(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        readArmState(tag);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeArmState(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null && pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag());
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        readArmState(tag);
    }

    private void writeArmState(CompoundTag tag) {
        tag.putBoolean("Moving", moving);
        tag.putLong("MoveStartTick", moveStartTick);
        tag.putLong("HandBlockPos", handBlockPos.asLong());
        tag.putLong("MoveStartBlockPos", moveStartBlockPos.asLong());
        tag.putLong("MoveTargetBlockPos", moveTargetBlockPos.asLong());
    }

    private void readArmState(CompoundTag tag) {
        moving = tag.getBoolean("Moving");
        moveStartTick = tag.getLong("MoveStartTick");
        handBlockPos = tag.contains("HandBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("HandBlockPos")) : worldPosition;
        moveStartBlockPos = tag.contains("MoveStartBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveStartBlockPos")) : handBlockPos;
        moveTargetBlockPos = tag.contains("MoveTargetBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveTargetBlockPos")) : handBlockPos;
    }
}
