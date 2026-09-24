package g_mungus.zps.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the counters behind the "Flying is not enabled on this server" kick.
 *
 * <p>A survival player held in a Tractor Beam hovers, and the server counts every tick of that toward the kick.
 * The beam resets the counters for as long as it has hold of them.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {

    @Accessor("aboveGroundTickCount")
    void zps$setAboveGroundTickCount(int ticks);

    @Accessor("aboveGroundVehicleTickCount")
    void zps$setAboveGroundVehicleTickCount(int ticks);
}
