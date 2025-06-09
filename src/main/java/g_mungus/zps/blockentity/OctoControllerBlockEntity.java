package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.entity.OctoMountingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OctoControllerBlockEntity extends RideableNetworkTerminal<OctoMountingEntity> implements RedstoneCapableTerminal{

    private final ConcurrentMap<Integer, Integer> outputSignals = new ConcurrentHashMap<>();

    public OctoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCTO_CONTROLLER.get(), pos, state);

        for (int i = Channels.getInitialChannel(8);
             i <= Channels.getFinalChannel(8);
             i++
        ) {
            outputSignals.put(i, 0);
        }
    }

    @Override
    EntityType<OctoMountingEntity> getSeatEntity() {
        return ModEntities.OCTO_MOUNTING.get();
    }

    @Override
    void registerSeatEntity(OctoMountingEntity seat) {
        seat.blockEntity = this;
        seat.isController = true;
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

    public void setA(int a) { supplySignal(Channels.OCT_A, a); }
    public void setB(int b) { supplySignal(Channels.OCT_B, b); }
    public void setC(int c) { supplySignal(Channels.OCT_C, c); }
    public void setD(int d) { supplySignal(Channels.OCT_D, d); }
    public void setE(int e) { supplySignal(Channels.OCT_E, e); }
    public void setF(int f) { supplySignal(Channels.OCT_F, f); }
    public void setG(int g) { supplySignal(Channels.OCT_G, g); }
    public void setH(int h) { supplySignal(Channels.OCT_H, h); }
}
