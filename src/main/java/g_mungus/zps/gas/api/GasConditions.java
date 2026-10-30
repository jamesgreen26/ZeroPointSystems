package g_mungus.zps.gas.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * What a {@link GasEnvironment} came to once every source had changed it: what was applied to the
 * entity. Kept for players so their debug screen can show it.
 *
 * @param temperatureK the temperature applied, or {@link Double#NaN} for none
 * @param gases        the gases present, each with its share of the whole, from 0 to 1
 * @param exposed      false if a source shielded the entity from all of it
 */
public record GasConditions(double temperatureK, boolean breathable, Map<ResourceLocation, Double> gases, boolean exposed) {

    public static void write(FriendlyByteBuf buffer, GasConditions conditions) {
        buffer.writeDouble(conditions.temperatureK);
        buffer.writeBoolean(conditions.breathable);
        buffer.writeMap(conditions.gases, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeDouble);
        buffer.writeBoolean(conditions.exposed);
    }

    public static GasConditions read(FriendlyByteBuf buffer) {
        return new GasConditions(buffer.readDouble(), buffer.readBoolean(),
                buffer.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readDouble), buffer.readBoolean());
    }

    public GasConditions {
        gases = Map.copyOf(gases);
    }

    public static GasConditions of(GasEnvironment environment) {
        Map<ResourceLocation, Double> gases = new HashMap<>(environment.fractions());
        gases.values().removeIf(share -> !(share > 0));
        return new GasConditions(environment.temperatureK(), environment.isBreathable(), gases, environment.isExposed());
    }
}
