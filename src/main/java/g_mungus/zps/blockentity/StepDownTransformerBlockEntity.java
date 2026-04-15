package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class StepDownTransformerBlockEntity extends NetworkTerminalImpl implements EnergyTransferBE {
    private static final long STALE_TRANSFER_TICKS = 2;

    private long refreshTick = Long.MIN_VALUE;
    private long lastTransferUpdateTick = Long.MIN_VALUE;
    private int hudInfo;

    public StepDownTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPDOWN_TRANSFORMER.get(), pos, state);
    }

    public void updateTransferRate(int info, long gameTime) {
        if (lastTransferUpdateTick == gameTime) {
            hudInfo += info;
        } else {
            hudInfo = info;
        }
        lastTransferUpdateTick = gameTime;
    }

    @Override
    public void setLastHudRefreshTick(long ticks) {
        refreshTick = ticks;
    }

    @Override
    public long getLastHudRefreshTick() {
        return refreshTick;
    }

    @Override
    public void provideInfo(Integer info) {
        hudInfo = info;
    }

    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide) {
            if (lastTransferUpdateTick == Long.MIN_VALUE || level.getGameTime() - lastTransferUpdateTick > STALE_TRANSFER_TICKS) {
                return 0;
            }
        }

        return hudInfo;
    }
}
