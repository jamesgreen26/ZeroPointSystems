package g_mungus.zps.gas.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * What a {@link GasEnvironment} came to once every source had changed it: what was applied to the
 * entity. Kept for players so their debug screen can show it.
 *
 * @param temperatureK the temperature applied, or {@link Double#NaN} for none
 * @param gases        the gases present, sorted by id
 * @param exposed      false if a source shielded the entity from all of it
 */
public record GasConditions(double temperatureK, boolean breathable, List<ResourceLocation> gases, boolean exposed) {

    public static void write(FriendlyByteBuf buffer, GasConditions conditions) {
        buffer.writeDouble(conditions.temperatureK);
        buffer.writeBoolean(conditions.breathable);
        buffer.writeCollection(conditions.gases, FriendlyByteBuf::writeResourceLocation);
        buffer.writeBoolean(conditions.exposed);
    }

    public static GasConditions read(FriendlyByteBuf buffer) {
        return new GasConditions(buffer.readDouble(), buffer.readBoolean(),
                buffer.readList(FriendlyByteBuf::readResourceLocation), buffer.readBoolean());
    }

    public GasConditions {
        gases = List.copyOf(gases);
    }

    public static GasConditions of(GasEnvironment environment) {
        List<ResourceLocation> gases = environment.fractions().entrySet().stream()
                .filter(gas -> gas.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return new GasConditions(environment.temperatureK(), environment.isBreathable(), gases, environment.isExposed());
    }
}
