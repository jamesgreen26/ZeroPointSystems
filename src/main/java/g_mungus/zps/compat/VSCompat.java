package g_mungus.zps.compat;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
    }
}
