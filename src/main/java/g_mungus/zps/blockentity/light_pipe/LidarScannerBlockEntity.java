package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.AbstractRadioBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.LidarScannerBlock;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import g_mungus.zps.lidar.LidarRaycasts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LidarScannerBlockEntity extends NetworkTerminalImpl implements LightPipeDataSender {

    private static final int GRID_SIZE = 30;
    private static final int TOTAL_RAYS = GRID_SIZE * GRID_SIZE;
    private static final int TICK_FREQUENCY = 4;
    private static final long SCAN_CACHE_LIFETIME_TICKS = 20L * 5L;
    private static final double MAX_CAST_DISTANCE = 512.0;
    private static final double HALF_FOV_DEGREES = 30.0;
    private static final double SPREAD = Math.tan(Math.toRadians(HALF_FOV_DEGREES));
    private static final ScanResult FAILED_SCAN = new ScanResult("", false);
    private static final ExecutorService LIDAR_SCAN_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "zps-lidar-scan");
        thread.setDaemon(true);
        return thread;
    });

    private final int tickOffset = new Random().nextInt(TICK_FREQUENCY);
    private String currentDisplayText = "";
    private @Nullable LidarRaycasts.ScanContext cachedScanContext;
    private long cachedScanContextExpiresAtGameTime = Long.MIN_VALUE;
    private @Nullable InFlightScan inFlightScan;

    public LidarScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIDAR_SCANNER.get(), pos, state);
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.is(ModBlocks.LIDAR_SCANNER.get())) {
            return;
        }

        applyCompletedScan(serverLevel);

        if ((serverLevel.getGameTime() + tickOffset) % TICK_FREQUENCY != 0 || inFlightScan != null) {
            return;
        }

        Direction facing = state.getValue(AbstractRadioBlock.FACING);
        LidarRaycasts.ScanContext scanContext = getScanContext(serverLevel);
        CompletableFuture<ScanResult> scanFuture = CompletableFuture.supplyAsync(
                () -> runScan(serverLevel, facing, scanContext),
                LIDAR_SCAN_EXECUTOR
        ).exceptionally(ignored -> FAILED_SCAN);
        inFlightScan = new InFlightScan(facing, scanFuture);
    }

    private void applyCompletedScan(ServerLevel serverLevel) {
        InFlightScan queuedScan = inFlightScan;
        if (queuedScan == null || !queuedScan.future().isDone()) {
            return;
        }
        inFlightScan = null;

        ScanResult scanResult;
        try {
            scanResult = queuedScan.future().getNow(FAILED_SCAN);
        } catch (Throwable ignored) {
            scanResult = FAILED_SCAN;
        }

        if (scanResult.encodedFrame().length() != TOTAL_RAYS || isRemoved()) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.is(ModBlocks.LIDAR_SCANNER.get()) || state.getValue(AbstractRadioBlock.FACING) != queuedScan.facing()) {
            return;
        }

        String nextDisplayText = scanResult.encodedFrame();
        boolean textChanged = !Objects.equals(currentDisplayText, nextDisplayText);
        if (textChanged) {
            currentDisplayText = nextDisplayText;
            updateClient();
        }

        boolean wasPowered = state.getValue(LidarScannerBlock.POWERED);
        boolean shouldBePowered = scanResult.hasHit();
        if (wasPowered != shouldBePowered) {
            serverLevel.setBlock(getBlockPos(), state.setValue(LidarScannerBlock.POWERED, shouldBePowered), Block.UPDATE_ALL);
        }

        if (textChanged || wasPowered != shouldBePowered) {
            updateSignal(serverLevel);
        }
    }

    private ScanResult runScan(ServerLevel level, Direction facing, LidarRaycasts.ScanContext scanContext) {
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
        Vec3 up = new Vec3(0.0, 1.0, 0.0);

        Vec3 start = Vec3.atCenterOf(getBlockPos()).add(forward.scale(0.55));

        StringBuilder encoded = new StringBuilder(TOTAL_RAYS);
        boolean hit = false;
        for (int row = 0; row < GRID_SIZE; row++) {
            if (Thread.currentThread().isInterrupted()) {
                return FAILED_SCAN;
            }
            double v = 1.0 - ((row + 0.5) / GRID_SIZE) * 2.0;
            for (int col = 0; col < GRID_SIZE; col++) {
                double u = ((col + 0.5) / GRID_SIZE) * 2.0 - 1.0;
                Vec3 direction = forward.add(right.scale(u * SPREAD)).add(up.scale(v * SPREAD)).normalize();
                double distance;
                try {
                    distance = LidarRaycasts.raycast(level, start, direction, MAX_CAST_DISTANCE, scanContext);
                } catch (Throwable ignored) {
                    return FAILED_SCAN;
                }
                if (distance >= 0.0) {
                    hit = true;
                }
                encoded.append(encodeDistance(distance));
            }
        }
        return new ScanResult(encoded.toString(), hit);
    }

    private LidarRaycasts.ScanContext getScanContext(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (cachedScanContext == null || gameTime >= cachedScanContextExpiresAtGameTime) {
            cachedScanContext = new LidarRaycasts.ScanContext();
            cachedScanContextExpiresAtGameTime = gameTime + SCAN_CACHE_LIFETIME_TICKS;
        }
        return cachedScanContext;
    }

    private static char encodeDistance(double distance) {
        if (distance < 0.0) {
            return '!';
        }
        double normalizedDistance = Mth.clamp(distance / MAX_CAST_DISTANCE, 0.0, 1.0);
        double closeness = 1.0 - normalizedDistance;
        int paletteIndex = Mth.clamp((int) Math.round(closeness * 63.0), 0, 63);
        return (char) (33 + paletteIndex);
    }

    @Override
    public String provideNextDisplayText(int length) {
        if (currentDisplayText.length() <= length) {
            return currentDisplayText;
        }
        return currentDisplayText.substring(0, length);
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            updateSignal(level);
        }
    }

    private void updateClient() {
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null && pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("DisplayText", currentDisplayText);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        currentDisplayText = tag.getString("DisplayText");
    }

    @Override
    public void setRemoved() {
        cancelInFlightScan();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        cancelInFlightScan();
        super.onChunkUnloaded();
    }

    private void cancelInFlightScan() {
        InFlightScan queuedScan = inFlightScan;
        inFlightScan = null;
        if (queuedScan != null) {
            queuedScan.future().cancel(true);
        }
    }

    private record InFlightScan(Direction facing, CompletableFuture<ScanResult> future) {
    }

    private record ScanResult(String encodedFrame, boolean hasHit) {
    }
}
