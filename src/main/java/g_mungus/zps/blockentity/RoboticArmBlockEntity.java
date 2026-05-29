package g_mungus.zps.blockentity;

import g_mungus.zps.ZPSMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class RoboticArmBlockEntity extends NetworkTerminalImpl implements Clearable {
    public static final int MOVE_TIME_TICKS = 15;
    public static final int MAX_DISTANCE_BLOCKS = 4;
    private static final int CONTAINER_CLOSE_DELAY_TICKS = 3;
    public static final int MIN_RETRIEVE_AMOUNT = 1;
    public static final int MAX_RETRIEVE_AMOUNT = 64;
    public static final int ENERGY_CAPACITY = 8192;
    public static final int ENERGY_PER_MOVE_TICK = 8;

    private boolean moving;
    private BlockPos handBlockPos;
    private BlockPos moveStartBlockPos;
    private BlockPos moveTargetBlockPos;
    private long moveStartTick;
    private ItemStack heldStack = ItemStack.EMPTY;
    private PendingTransfer pendingTransfer = PendingTransfer.NONE;
    private BlockPos pendingTransferTargetPos = BlockPos.ZERO;
    private int retrieveAmount = 1;
    private boolean viewRange = false;
    private boolean energyRanOutDuringMove = false;
    private Vec3 lastSwivelAxis = new Vec3(1.0, 0.0, 0.0);
    private transient FakePlayer usePlayer;
    private final RoboticArmEnergyStorage energyStorage;
    private final LazyOptional<IEnergyStorage> energyCapability;
    private static final ConcurrentLinkedQueue<DelayedContainerCloseJob> delayedContainerCloseJobs = new ConcurrentLinkedQueue<>();
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
        this.energyStorage = new RoboticArmEnergyStorage();
        this.energyCapability = LazyOptional.of(() -> energyStorage);
        handBlockPos = pos.above();
        moveStartBlockPos = pos;
        moveTargetBlockPos = pos;
    }

    public void tickServer() {
        if (level == null) return;

        long gameTime = level.getGameTime();

        if (moving) {
            int extracted = energyStorage.consumeInternal(ENERGY_PER_MOVE_TICK);
            if (extracted < ENERGY_PER_MOVE_TICK) {
                energyRanOutDuringMove = true;
            }
        }

        if (moving && gameTime - moveStartTick >= MOVE_TIME_TICKS) {
            moving = false;
            handBlockPos = moveTargetBlockPos;
            boolean canCompletePendingAction = !energyRanOutDuringMove || energyStorage.getEnergyStored() > ENERGY_PER_MOVE_TICK;
            if (canCompletePendingAction) {
                runPendingTransfer();
            } else {
                pendingTransfer = PendingTransfer.NONE;
            }
            energyRanOutDuringMove = false;
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

    public BlockPos getCurrentTargetBlockPos() {
        if (moving) {
            return moveTargetBlockPos;
        }
        if (pendingTransfer != PendingTransfer.NONE) {
            return pendingTransferTargetPos;
        }
        return handBlockPos;
    }

    public boolean moveHandTo(BlockPos newBlockPos) {
        if (level == null || moving) return false;
        if (newBlockPos.distSqr(worldPosition) > (double) (MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS)) return false;
        if (energyStorage.getEnergyStored() <= ENERGY_PER_MOVE_TICK) return false;

        moveStartBlockPos = handBlockPos;
        moveTargetBlockPos = newBlockPos;
        moveStartTick = level.getGameTime();
        moving = true;
        energyRanOutDuringMove = false;
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

    public boolean UseAt(BlockPos targetPos) {
        pendingTransfer = PendingTransfer.USE;
        pendingTransferTargetPos = targetPos;
        return moveHandTo(targetPos);
    }

    public boolean ShiftUseAt(BlockPos targetPos) {
        pendingTransfer = PendingTransfer.SHIFT_USE;
        pendingTransferTargetPos = targetPos;
        return moveHandTo(targetPos);
    }

    public boolean DropItemsAt(BlockPos targetPos) {
        pendingTransfer = PendingTransfer.DROP;
        pendingTransferTargetPos = targetPos;
        return moveHandTo(targetPos);
    }

    public Container getHeldStackAccess() {
        return heldStackAccess;
    }

    public ItemStack getHeldStack() {
        return heldStack;
    }

    public Vec3 getLastSwivelAxis() {
        return lastSwivelAxis;
    }

    public void setLastSwivelAxis(Vec3 axis) {
        if (axis.lengthSqr() <= 1.0e-8) return;
        lastSwivelAxis = axis.normalize();
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

    @Override
    public @NotNull AABB getRenderBoundingBox() {
        int r = MAX_DISTANCE_BLOCKS;
        return new AABB(
                worldPosition.getX() - r,
                worldPosition.getY() - r,
                worldPosition.getZ() - r,
                worldPosition.getX() + r + 1.0,
                worldPosition.getY() + r + 1.0,
                worldPosition.getZ() + r + 1.0
        );
    }

    private void writeArmState(CompoundTag tag) {
        tag.putBoolean("Moving", moving);
        tag.putLong("MoveStartTick", moveStartTick);
        tag.putLong("HandBlockPos", handBlockPos.asLong());
        tag.putLong("MoveStartBlockPos", moveStartBlockPos.asLong());
        tag.putLong("MoveTargetBlockPos", moveTargetBlockPos.asLong());
        tag.putInt("PendingTransfer", pendingTransfer.ordinal());
        tag.putLong("PendingTransferTargetPos", pendingTransferTargetPos.asLong());
        tag.putInt("RetrieveAmount", retrieveAmount);
        tag.putBoolean("ViewRange", viewRange);
        tag.put("Energy", energyStorage.serializeNBT());
    }

    private void readArmState(CompoundTag tag) {
        moving = tag.getBoolean("Moving");
        moveStartTick = tag.getLong("MoveStartTick");
        handBlockPos = tag.contains("HandBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("HandBlockPos")) : worldPosition;
        moveStartBlockPos = tag.contains("MoveStartBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveStartBlockPos")) : handBlockPos;
        moveTargetBlockPos = tag.contains("MoveTargetBlockPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("MoveTargetBlockPos")) : handBlockPos;
        pendingTransfer = PendingTransfer.byOrdinal(tag.getInt("PendingTransfer"));
        pendingTransferTargetPos = tag.contains("PendingTransferTargetPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("PendingTransferTargetPos")) : BlockPos.ZERO;
        retrieveAmount = clampRetrieveAmount(tag.contains("RetrieveAmount", Tag.TAG_ANY_NUMERIC) ? tag.getInt("RetrieveAmount") : 1);
        viewRange = tag.getBoolean("ViewRange");
        if (tag.contains("Energy")) {
            energyStorage.deserializeNBT(tag.get("Energy"));
        }
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

        if (pendingTransfer == PendingTransfer.USE) {
            tryUseAt(pendingTransferTargetPos, false);
            pendingTransfer = PendingTransfer.NONE;
            return;
        }

        if (pendingTransfer == PendingTransfer.SHIFT_USE) {
            tryUseAt(pendingTransferTargetPos, true);
            pendingTransfer = PendingTransfer.NONE;
            return;
        }

        if (pendingTransfer == PendingTransfer.DROP) {
            tryDropAt(pendingTransferTargetPos);
            pendingTransfer = PendingTransfer.NONE;
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pendingTransferTargetPos);
        Container container = resolveTransferContainer(pendingTransferTargetPos);
        if (container == null) {
            pendingTransfer = PendingTransfer.NONE;
            return;
        }

        Player interactionPlayer = getContainerInteractionPlayer();
        openContainerForVisualTransfer(blockEntity, interactionPlayer);
        try {
            if (pendingTransfer == PendingTransfer.RETRIEVE) {
                tryRetrieveFrom(container);
            } else if (pendingTransfer == PendingTransfer.DEPOSIT) {
                tryDepositInto(container);
            }
        } finally {
            scheduleContainerClose(blockEntity.getBlockPos(), gameTimeWithDelay(), interactionPlayer);
        }
        pendingTransfer = PendingTransfer.NONE;
    }

    private @Nullable Container resolveTransferContainer(BlockPos targetPos) {
        if (level == null) return null;
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, targetState, level, targetPos, true);
        }

        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        if (blockEntity instanceof Container container) {
            return container;
        }
        return null;
    }

    private long gameTimeWithDelay() {
        return level != null ? level.getGameTime() + CONTAINER_CLOSE_DELAY_TICKS : CONTAINER_CLOSE_DELAY_TICKS;
    }

    private void scheduleContainerClose(BlockPos blockPos, long closeGameTime, @Nullable Player player) {
        if (player == null) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        delayedContainerCloseJobs.add(new DelayedContainerCloseJob(serverLevel, blockPos.immutable(), closeGameTime, player));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || delayedContainerCloseJobs.isEmpty()) return;
        for (DelayedContainerCloseJob delayedClose : delayedContainerCloseJobs) {
            if (delayedClose.level.getGameTime() < delayedClose.closeGameTime) continue;
            BlockEntity blockEntity = delayedClose.level.getBlockEntity(delayedClose.blockPos);
            if (blockEntity != null) {
                closeContainerAfterVisualTransfer(blockEntity, delayedClose.player);
            }
            delayedContainerCloseJobs.remove(delayedClose);
        }
    }

    private @Nullable Player getContainerInteractionPlayer() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return getOrCreateUsePlayer(serverLevel);
    }

    private static void openContainerForVisualTransfer(BlockEntity blockEntity, @Nullable Player player) {
        if (player == null) return;
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.startOpen(player);
        } else if (blockEntity instanceof BarrelBlockEntity barrel) {
            barrel.startOpen(player);
        }
    }

    private static void closeContainerAfterVisualTransfer(BlockEntity blockEntity, @Nullable Player player) {
        if (player == null) return;
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.stopOpen(player);
        } else if (blockEntity instanceof BarrelBlockEntity barrel) {
            barrel.stopOpen(player);
        }
    }

    private void tryRetrieveFrom(Container source) {
        int desired = retrieveAmount;
        int moved = 0;
        if (!heldStack.isEmpty() && heldStack.getCount() >= heldStack.getMaxStackSize()) return;

        for (int slot = 0; slot < source.getContainerSize() && moved < desired; slot++) {
            ItemStack sourceStack = source.getItem(slot);
            if (sourceStack.isEmpty()) continue;
            if (!canTakeItem(source, slot, sourceStack, Direction.DOWN)) continue;
            if (!heldStack.isEmpty() && !ItemStack.isSameItemSameTags(heldStack, sourceStack)) continue;

            int maxByHeld = heldStack.isEmpty() ? sourceStack.getMaxStackSize() : heldStack.getMaxStackSize() - heldStack.getCount();
            int toExtract = Math.min(desired - moved, Math.min(sourceStack.getCount(), maxByHeld));
            if (toExtract <= 0) break;

            ItemStack extracted = source.removeItem(slot, toExtract);
            if (extracted.isEmpty()) continue;

            if (heldStack.isEmpty()) {
                heldStack = extracted;
            } else {
                heldStack.grow(extracted.getCount());
            }
            moved += extracted.getCount();

            source.setChanged();
            setChanged();
        }
    }

    public int getRetrieveAmount() {
        return retrieveAmount;
    }

    public boolean isViewRange() {
        return viewRange;
    }

    public void setArmSettings(int retrieveAmount, boolean viewRange) {
        this.retrieveAmount = clampRetrieveAmount(retrieveAmount);
        this.viewRange = viewRange;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private static int clampRetrieveAmount(int value) {
        return Math.max(MIN_RETRIEVE_AMOUNT, Math.min(MAX_RETRIEVE_AMOUNT, value));
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    private void onEnergyChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    private void tryDepositInto(Container target) {
        if (heldStack.isEmpty()) return;
        int desired = Math.min(retrieveAmount, heldStack.getCount());
        int moved = 0;

        for (int slot = 0; slot < target.getContainerSize() && moved < desired; slot++) {
            if (!canPlaceItem(target, slot, heldStack, Direction.UP)) continue;

            ItemStack targetStack = target.getItem(slot);
            if (!targetStack.isEmpty() && !ItemStack.isSameItemSameTags(targetStack, heldStack)) {
                continue;
            }

            int freeSpace = targetStack.isEmpty()
                    ? heldStack.getMaxStackSize()
                    : targetStack.getMaxStackSize() - targetStack.getCount();
            if (freeSpace <= 0) continue;

            int toMove = Math.min(desired - moved, Math.min(heldStack.getCount(), freeSpace));
            if (toMove <= 0) continue;

            if (targetStack.isEmpty()) {
                ItemStack inserted = heldStack.copy();
                inserted.setCount(toMove);
                target.setItem(slot, inserted);
            } else {
                targetStack.grow(toMove);
                target.setItem(slot, targetStack);
            }

            heldStack.shrink(toMove);
            moved += toMove;
            if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;

            target.setChanged();
            setChanged();
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

    private void tryUseAt(BlockPos targetPos, boolean shiftUse) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        FakePlayer fakePlayer = getOrCreateUsePlayer(serverLevel);
        fakePlayer.setShiftKeyDown(shiftUse);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, heldStack.copy());

        Direction hitFace = getHitFace(targetPos);
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        fakePlayer.setPos(targetCenter.x, targetCenter.y, targetCenter.z);

        BlockHitResult hitResult = new BlockHitResult(targetCenter, hitFace, targetPos, false);
        ItemStack heldByPlayer = fakePlayer.getMainHandItem();
        if (!heldByPlayer.isEmpty() && heldByPlayer.getItem() instanceof BlockItem) {
            BlockState targetState = serverLevel.getBlockState(targetPos);
            BlockPlaceContext placeContext = new BlockPlaceContext(new UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, hitResult));
            if (!targetState.canBeReplaced(placeContext)) {
                fakePlayer.setShiftKeyDown(false);
                return;
            }
        }

        InteractionResult result = fakePlayer.gameMode.useItemOn(
                fakePlayer,
                serverLevel,
                heldByPlayer,
                InteractionHand.MAIN_HAND,
                hitResult
        );

        if (!result.consumesAction()) {
            fakePlayer.gameMode.useItem(
                    fakePlayer,
                    serverLevel,
                    fakePlayer.getMainHandItem(),
                    InteractionHand.MAIN_HAND
            );
        }

        heldStack = fakePlayer.getMainHandItem().copy();
        fakePlayer.setShiftKeyDown(false);
        setChanged();
    }

    private Direction getHitFace(BlockPos targetPos) {
        int dx = worldPosition.getX() - targetPos.getX();
        int dy = worldPosition.getY() - targetPos.getY();
        int dz = worldPosition.getZ() - targetPos.getZ();
        if (dx == 0 && dy == 0 && dz == 0) return Direction.UP;
        return Direction.getNearest(dx, dy, dz);
    }

    private FakePlayer getOrCreateUsePlayer(ServerLevel serverLevel) {
        if (usePlayer == null || usePlayer.level() != serverLevel) {
            usePlayer = new FakePlayer(
                    serverLevel,
                    new GameProfile(UUID.fromString("c4ec0cd8-0975-4cd9-a413-6eecbbfb4f9d"), "[ZPS Robotic Arm]")
            );
        }
        return usePlayer;
    }

    private void tryDropAt(BlockPos targetPos) {
        if (level == null || level.isClientSide) return;
        if (heldStack.isEmpty()) return;

        ItemStack dropStack = heldStack.copy();
        heldStack = ItemStack.EMPTY;

        Vec3 center = Vec3.atCenterOf(targetPos);
        ItemEntity itemEntity = new ItemEntity(level, center.x, center.y, center.z, dropStack);
        itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(itemEntity);
        setChanged();
    }

    public void dropHeldStackAtHandPosition() {
        if (level == null || level.isClientSide) return;
        if (heldStack.isEmpty()) return;

        ItemStack dropStack = heldStack.copy();
        heldStack = ItemStack.EMPTY;

        Vec3 dropPosition = getCurrentHandWorldPosition();
        ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, dropStack);
        itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(itemEntity);
        setChanged();
    }

    private Vec3 getCurrentHandWorldPosition() {
        Vec3 settled = Vec3.atCenterOf(handBlockPos);
        if (!moving || level == null) return settled;

        double elapsed = level.getGameTime() - moveStartTick;
        double progress = Math.min(1.0D, Math.max(0.0D, elapsed / (double) MOVE_TIME_TICKS));
        Vec3 start = Vec3.atCenterOf(moveStartBlockPos);
        Vec3 end = Vec3.atCenterOf(moveTargetBlockPos);
        return start.lerp(end, progress);
    }

    private enum PendingTransfer {
        NONE,
        RETRIEVE,
        DEPOSIT,
        USE,
        SHIFT_USE,
        DROP;

        static PendingTransfer byOrdinal(int value) {
            if (value < 0 || value >= values().length) return NONE;
            return values()[value];
        }
    }

    private record DelayedContainerCloseJob(ServerLevel level, BlockPos blockPos, long closeGameTime, Player player) {}

    private class RoboticArmEnergyStorage extends EnergyStorage {
        private RoboticArmEnergyStorage() {
            super(ENERGY_CAPACITY, ENERGY_CAPACITY, 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                onEnergyChanged();
            }
            return received;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        private int consumeInternal(int amount) {
            int extracted = Math.min(energy, Math.max(0, amount));
            if (extracted > 0) {
                energy -= extracted;
                onEnergyChanged();
            }
            return extracted;
        }
    }
}
