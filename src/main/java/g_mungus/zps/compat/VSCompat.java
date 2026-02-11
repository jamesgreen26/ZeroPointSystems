package g_mungus.zps.compat;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.command.ShipArgument;
import org.valkyrienskies.mod.common.command.ShipSelector;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.Comparator;
import java.util.Optional;

public class VSCompat {

    /// Only call after verifying that VS is loaded
    static Vec3 shipToWorld(ServerLevel level, BlockPos pos) {
        return VSGameUtilsKt.toWorldCoordinates(level, pos);
    }

    static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        event.register(new ScriptGetter<>(
                "ship",
                Ship.class,
                ZPSMod.resource("ship"),
                context -> VSGameUtilsKt.getShipManagingPos(context.level(), context.pos())
        ));

        event.register(new ScriptMapper2<>(
                "==",
                Ship.class,
                Boolean.class,
                ZPSMod.resource("ship"),
                ZPSMod.resource("boolean"),
                (ship, context) -> {
                    Optional<Ship> other = context.argumentValue().select(VSGameUtilsKt.getShipObjectWorld(context.level()).getAllShips()).stream().min(Comparator.comparingDouble(
                            value -> value.getTransform().getPosition().distanceSquared(VectorConversionsMCKt.toJOML(context.pos().getCenter()))
                    ));

                    if (other.isEmpty() || ship == null) {
                        return false;
                    } else {
                        return ship.equals(other.get());
                    }
                },
                ShipArgument.ships(),
                ShipSelector.class
        ));

        // Ship position as Vec3
        event.register(new ScriptMapper<>(
                "position",
                Ship.class,
                Vec3.class,
                ZPSMod.resource("ship"),
                ZPSMod.resource("vec_pos"),
                (ship, context) -> VectorConversionsMCKt.toMinecraft(ship.getTransform().getPositionInWorld())
        ));

        // Ship velocity as Vec3
        event.register(new ScriptMapper<>(
                "velocity",
                Ship.class,
                Vec3.class,
                ZPSMod.resource("ship"),
                ZPSMod.resource("vec_dir"),
                (ship, context) -> VectorConversionsMCKt.toMinecraft(ship.getVelocity())
        ));

        // Ship box dimensions as Vec3
        event.register(new ScriptMapper<>(
                "bounding_box",
                Ship.class,
                Vec3.class,
                ZPSMod.resource("ship"),
                ZPSMod.resource("vec_box"),
                (ship, context) -> {
                    AABBic aabb = ship.getShipAABB();
                    assert aabb != null;
                    return new Vec3(
                            aabb.maxX() - aabb.minX(),
                            aabb.maxY() - aabb.minY(),
                            aabb.maxZ() - aabb.minZ()
                    ).scale(ship.getTransform().getShipToWorldScaling().x());
                }
        ));

        // Ship mass scaled by shipToWorldScaling volume
        event.register(new ScriptMapper<>(
                "mass",
                Ship.class,
                Double.class,
                ZPSMod.resource("ship"),
                ZPSMod.resource("double"),
                (ship, context) -> {
                    double mass = ((ServerShip)ship).getInertiaData().getMass();
                    org.joml.Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
                    double scalingVolume = scaling.x() * scaling.y() * scaling.z();
                    return mass / scalingVolume;
                }
        ));
    }
}
