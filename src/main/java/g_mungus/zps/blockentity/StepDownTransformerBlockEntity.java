package g_mungus.zps.blockentity;

import g_mungus.zps.util.TickAverage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class StepDownTransformerBlockEntity extends NetworkTerminalImpl implements EnergyTransferBE {

    private long refreshTick = Long.MIN_VALUE;
    private final TickAverage transferAverage = new TickAverage(HUD_AVERAGE_WINDOW_TICKS);
    private int hudInfo;

    public StepDownTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPDOWN_TRANSFORMER.get(), pos, state);
    }

    public void updateTransferRate(int info, long gameTime) {
        transferAverage.record(info, gameTime);
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
            return transferAverage.average(level.getGameTime());
        }

        return hudInfo;
    }
}
