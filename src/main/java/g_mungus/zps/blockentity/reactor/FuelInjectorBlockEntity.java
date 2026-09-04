package g_mungus.zps.blockentity.reactor;

import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.OneWayCompositeDuctEdge;
import g_mungus.zps.block.reactor.ReactorGasWallBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorChamberNode;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.ConnectionType;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * Keeps the injector's one-way edge into the chamber in place. The edge cannot be negotiated
 * face-to-face like every other gas edge — the chamber node is somewhere inside the cavity, not
 * next door — so it is authored here and torn down by the reactor when it dissolves.
 */
public class FuelInjectorBlockEntity extends GasNodeBlockEntity {

    /** A duct's bore over one block: the stub's own half plus the wall's. */
    private static final double EDGE_RADIUS = 0.125;
    private static final double EDGE_LENGTH = 0.5;

    public FuelInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_INJECTOR.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        syncNodeState();
        ensureChamberEdge(serverLevel);
    }

    /** The reactor this injector feeds, or null if it is not facing into one. */
    public Reactor reactor(ServerLevel serverLevel) {
        return ReactorManager.get(serverLevel).reactorServedBy(worldPosition, ReactorGasWallBlock.facing(getBlockState()));
    }

    private void ensureChamberEdge(ServerLevel serverLevel) {
        Reactor reactor = reactor(serverLevel);
        if (reactor == null) {
            return;
        }
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos own = getDuctNodePosition();
        DuctNodePos host = reactor.hostNodePos(serverLevel);
        if (kelvin.getNodeAt(own) == null
                || !(kelvin.getNodeAt(host) instanceof ReactorChamberNode)
                || kelvin.getEdgeBetween(own, host) != null) {
            return;
        }

        GasEdgeNegotiator.EdgeKey key = GasEdgeNegotiator.canonical(own, host);
        OneWayCompositeDuctEdge edge = new OneWayCompositeDuctEdge(ConnectionType.ONEWAY, key.a(), key.b(),
                EDGE_RADIUS, EDGE_LENGTH);
        // Kelvin: reversed == false lets gas flow from nodeA to nodeB. We want stub -> chamber.
        edge.setReversed(!key.a().equals(own));
        kelvin.addEdge(key.a(), key.b(), edge);
    }
}
