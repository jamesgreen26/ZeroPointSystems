package g_mungus.zps.mixin.vs;

import g_mungus.zps.entity.RiderOffsetVehicle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider;

/**
 * Makes every {@link RiderOffsetVehicle} tell Valkyrien Skies where its rider goes on a ship, which
 * VS otherwise takes to be straight above the vehicle, ignoring {@code positionRider}.
 */
@Mixin(value = RiderOffsetVehicle.class, remap = false)
public interface RiderOffsetExtension extends ShipMountedToDataProvider {

    @Override
    default ShipMountedToData provideShipMountedToData(Entity passenger, Float partialTicks) {
        Entity vehicle = (Entity) this;
        LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(vehicle.level(),
                new Vector3d(vehicle.getX(), vehicle.getY(), vehicle.getZ()));
        if (ship == null) {
            return null;
        }
        Vec3 spot = ((RiderOffsetVehicle) this).riderPosition(passenger, partialTicks == null ? 0f : partialTicks);
        return new ShipMountedToData(ship, new Vector3d(spot.x, spot.y, spot.z));
    }
}
