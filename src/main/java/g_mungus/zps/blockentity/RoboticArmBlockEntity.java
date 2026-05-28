package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RoboticArmBlockEntity extends NetworkTerminalImpl implements Clearable {
    public static final int MOVE_TIME_TICKS = 15;
    public static final int MAX_DISTANCE_BLOCKS = 4;

    private boolean moving;
    private BlockPos handBlockPos;
    private BlockPos moveStartBlockPos;
    private BlockPos moveTargetBlockPos;
    private long moveStartTick;
    private ItemStack heldStack = ItemStack.EMPTY;
    private PendingTransfer pendingTransfer = PendingTransfer.NONE;
    private BlockPos pendingTransferTargetPos = BlockPos.ZERO;
    private final Container heldStackAccess = new Container() {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return RoboticArmBlockEntity.this.heldStack.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? RoboticArmBlockEntity.this.heldStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot != 0) return ItemStack.EMPTY;
            ItemStack split = RoboticArmBlockEntity.this.heldStack.split(amount);
            if (!split.isEmpty()) {
                RoboticArmBlockEntity.this.setChanged();
            }
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot != 0) return ItemStack.EMPTY;
            ItemStack existing = RoboticArmBlockEntity.this.heldStack;
            RoboticArmBlockEntity.this.heldStack = ItemStack.EMPTY;
            RoboticArmBlockEntity.this.setChanged();
            return existing;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot != 0) return;
            RoboticArmBlockEntity.this.heldStack = stack;
            RoboticArmBlockEntity.this.setChanged();
        }

        @Override
        public void setChanged() {
            RoboticArmBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(RoboticArmBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            RoboticArmBlockEntity.this.heldStack = ItemStack.EMPTY;
            RoboticArmBlockEntity.this.setChanged();
        }
    };

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
            runPendingTransfer();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    public boolean RetrieveItemsFrom(BlockPos targetPos) {
        pendingTransfer = PendingTransfer.RETRIEVE;
        pendingTransferTargetPos = targetPos;
        return moveHandTo(targetPos);
    }

    public boolean DepositItemsAt(BlockPos targetPos) {
        pendingTransfer = PendingTransfer.DEPOSIT;
        pendingTransferTargetPos = targetPos;
        return moveHandTo(targetPos);
    }

    public Container getHeldStackAccess() {
        return heldStackAccess;
    }

    public ItemStack getHeldStack() {
        return heldStack;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeArmState(tag);
        writeInventoryState(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        readArmState(tag);
        readInventoryState(tag);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeArmState(tag);
        writeInventoryState(tag);
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
        readInventoryState(tag);
    }

    @Override
    public void clearContent() {
        heldStack = ItemStack.EMPTY;
        setChanged();
    }

    private void writeArmState(CompoundTag tag) {
        tag.putBoolean("Moving", moving);
        tag.putLong("MoveStartTick", moveStartTick);
        tag.putLong("HandBlockPos", handBlockPos.asLong());
        tag.putLong("MoveStartBlockPos", moveStartBlockPos.asLong());
        tag.putLong("MoveTargetBlockPos", moveTargetBlockPos.asLong());
        tag.putInt("PendingTransfer", pendingTransfer.ordinal());
        tag.putLong("PendingTransferTargetPos", pendingTransferTargetPos.asLong());
    }

    private void readArmState(CompoundTag tag) {
        moving = tag.getBoolean("Moving");
        moveStartTick = tag.getLong("MoveStartTick");
        handBlockPos = tag.contains("HandBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("HandBlockPos")) : worldPosition;
        moveStartBlockPos = tag.contains("MoveStartBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveStartBlockPos")) : handBlockPos;
        moveTargetBlockPos = tag.contains("MoveTargetBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveTargetBlockPos")) : handBlockPos;
        pendingTransfer = PendingTransfer.byOrdinal(tag.getInt("PendingTransfer"));
        pendingTransferTargetPos = tag.contains("PendingTransferTargetPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("PendingTransferTargetPos")) : BlockPos.ZERO;
    }

    private void writeInventoryState(CompoundTag tag) {
        if (!heldStack.isEmpty()) {
            tag.put("HeldStack", heldStack.save(new CompoundTag()));
        }
    }

    private void readInventoryState(CompoundTag tag) {
        if (tag.contains("HeldStack", Tag.TAG_COMPOUND)) {
            heldStack = ItemStack.of(tag.getCompound("HeldStack"));
        } else {
            heldStack = ItemStack.EMPTY;
        }
    }

    private void runPendingTransfer() {
        if (level == null || pendingTransfer == PendingTransfer.NONE) return;
        if (!handBlockPos.equals(pendingTransferTargetPos)) return;

        BlockEntity blockEntity = level.getBlockEntity(pendingTransferTargetPos);
        if (!(blockEntity instanceof Container container)) {
            pendingTransfer = PendingTransfer.NONE;
            return;
        }

        if (pendingTransfer == PendingTransfer.RETRIEVE) {
            tryRetrieveFrom(container);
        } else if (pendingTransfer == PendingTransfer.DEPOSIT) {
            tryDepositInto(container);
        }
        pendingTransfer = PendingTransfer.NONE;
    }

    private void tryRetrieveFrom(Container source) {
        if (!heldStack.isEmpty() && heldStack.getCount() >= heldStack.getMaxStackSize()) return;

        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            ItemStack sourceStack = source.getItem(slot);
            if (sourceStack.isEmpty()) continue;
            if (!canTakeItem(source, slot, sourceStack, Direction.DOWN)) continue;
            if (!heldStack.isEmpty() && !ItemStack.isSameItemSameTags(heldStack, sourceStack)) continue;

            ItemStack extracted = source.removeItem(slot, 1);
            if (extracted.isEmpty()) continue;

            if (heldStack.isEmpty()) {
                heldStack = extracted;
            } else {
                heldStack.grow(extracted.getCount());
            }

            source.setChanged();
            setChanged();
            return;
        }
    }

    private void tryDepositInto(Container target) {
        if (heldStack.isEmpty()) return;

        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            if (!canPlaceItem(target, slot, heldStack, Direction.UP)) continue;

            ItemStack targetStack = target.getItem(slot);
            if (!targetStack.isEmpty() && (!ItemStack.isSameItemSameTags(targetStack, heldStack) || targetStack.getCount() >= targetStack.getMaxStackSize())) {
                continue;
            }

            ItemStack one = heldStack.copy();
            one.setCount(1);

            if (targetStack.isEmpty()) {
                target.setItem(slot, one);
            } else {
                targetStack.grow(1);
                target.setItem(slot, targetStack);
            }

            heldStack.shrink(1);
            if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;

            target.setChanged();
            setChanged();
            return;
        }
    }

    private static boolean canTakeItem(Container container, int slot, ItemStack stack, Direction side) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            int[] slots = worldlyContainer.getSlotsForFace(side);
            boolean canAccessSlot = false;
            for (int allowedSlot : slots) {
                if (allowedSlot == slot) {
                    canAccessSlot = true;
                    break;
                }
            }
            if (!canAccessSlot) return false;
            return worldlyContainer.canTakeItemThroughFace(slot, stack, side);
        }
        return container.canTakeItem(container, slot, stack);
    }

    private static boolean canPlaceItem(Container container, int slot, ItemStack stack, Direction side) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            int[] slots = worldlyContainer.getSlotsForFace(side);
            boolean canAccessSlot = false;
            for (int allowedSlot : slots) {
                if (allowedSlot == slot) {
                    canAccessSlot = true;
                    break;
                }
            }
            if (!canAccessSlot) return false;
            return worldlyContainer.canPlaceItemThroughFace(slot, stack, side);
        }
        return container.canPlaceItem(slot, stack);
    }

    private enum PendingTransfer {
        NONE,
        RETRIEVE,
        DEPOSIT;

        static PendingTransfer byOrdinal(int value) {
            if (value < 0 || value >= values().length) return NONE;
            return values()[value];
        }
    }
}
