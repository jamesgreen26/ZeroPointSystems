package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.entity.DodecaMountingEntity;
import g_mungus.zps.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DodecaControllerBlockEntity extends RideableNetworkTerminal<DodecaMountingEntity> implements RedstoneSendingTerminal {

    private final ConcurrentMap<Integer, Integer> outputSignals = new ConcurrentHashMap<>();

    public DodecaControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DODECA_CONTROLLER.get(), pos, state);

        for (int i = Channels.getInitialChannel(12);
             i <= Channels.getFinalChannel(12);
             i++
        ) {
            outputSignals.put(i, 0);
            supplySignal(i, 0);
        }
    }

    @Override
    EntityType<DodecaMountingEntity> getSeatEntity() {
        return ModEntities.DODECA_MOUNTING.get();
    }

    @Override
    void registerSeatEntity(DodecaMountingEntity seat, Vec3i offset) {
        seat.blockEntity = this;
        seat.isController = true;
        seat.offset = offset;
    }

    @Override
    public int getCurrentSuppliedSignal(int channel) {
        return outputSignals.get(channel);
    }

    void supplySignal(int channel, int strength) {
        if (outputSignals.get(channel) == strength) return;
        outputSignals.put(channel, strength);
        updateAllSignals(level, getTerminals(channel));
    }

    public void setA(int a) { supplySignal(Channels.DOD_A, a); }
    public void setB(int b) { supplySignal(Channels.DOD_B, b); }
    public void setC(int c) { supplySignal(Channels.DOD_C, c); }
    public void setD(int d) { supplySignal(Channels.DOD_D, d); }
    public void setE(int e) { supplySignal(Channels.DOD_E, e); }
    public void setF(int f) { supplySignal(Channels.DOD_F, f); }
    public void setG(int g) { supplySignal(Channels.DOD_G, g); }
    public void setH(int h) { supplySignal(Channels.DOD_H, h); }
    public void setI(int i) { supplySignal(Channels.DOD_I, i); }
    public void setJ(int j) { supplySignal(Channels.DOD_J, j); }
    public void setK(int k) { supplySignal(Channels.DOD_K, k); }
    public void setL(int l) { supplySignal(Channels.DOD_L, l); }
}
