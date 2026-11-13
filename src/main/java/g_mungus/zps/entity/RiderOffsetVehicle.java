package g_mungus.zps.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle whose rider is not straight above it. Vanilla and Sable go through
 * {@code positionRider}, but Valkyrien Skies puts anyone mounted on a ship at the vehicle plus its
 * riding offset, straight up; {@code vs.RiderOffsetExtension} hands it this position instead.
 */
public interface RiderOffsetVehicle {
    /** Where the passenger's feet go, in the vehicle's own space (a ship's shipyard when on one). */
    Vec3 riderPosition(Entity passenger, float partialTick);
}
