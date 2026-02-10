package g_mungus.zps.commands.lang.v2.compat.valkyrienskies;

import g_mungus.zps.commands.lang.v2.MappedArgumentType;
import g_mungus.zps.commands.lang.v2.classes.ScriptClass;
import g_mungus.zps.commands.lang.v2.classes.ScriptObject;
import g_mungus.zps.commands.lang.v2.comparators.ScriptComparator;
import g_mungus.zps.commands.lang.v2.functions.ScriptFunction;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.command.ShipArgument;
import org.valkyrienskies.mod.common.command.ShipSelector;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.Comparator;
import java.util.List;

public record ShipClass(String name) implements ScriptClass<Ship> {

    @Override
    public Class<Ship> getType() {
        return Ship.class;
    }

    @Override
    public MappedArgumentType<?, Ship> getArgumentType() {
        return new MappedArgumentType<>(
                ShipArgument.ships(),
                ((shipSelector, commandSourceStack) ->
                        shipSelector.select(VSGameUtilsKt.getShipObjectWorld(commandSourceStack.getLevel()).getAllShips()).stream()
                                .min(Comparator.comparingDouble((a) -> a.getTransform().getPosition().distanceSquared(VectorConversionsMCKt.toJOML(commandSourceStack.getPosition()))))
                                .orElseThrow()),
                ShipSelector.class
        );
    }

    @Override
    public List<ScriptComparator<Ship>> getComparators() {
        return List.of();
    }

    @Override
    public List<ScriptFunction<Ship, ?>> getFunctions() {
        return List.of(
                new ScriptFunction<>("POS", (shipClass, context) ->
                        ScriptObject.withDefaultType("POS",
                                VectorConversionsMCKt.toMinecraft(shipClass.value().getTransform().getPosition()))),
                new ScriptFunction<>("VELOCITY", (shipClass, context) ->
                        ScriptObject.withDefaultType("VELOCITY",
                                VectorConversionsMCKt.toMinecraft(shipClass.value().getVelocity())))
                //todo: add more
        );
    }
}
