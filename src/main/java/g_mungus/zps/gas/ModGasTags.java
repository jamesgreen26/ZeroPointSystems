package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.Map;

/** Tags on Kelvin's {@code kelvin:gas_type} registry, under {@code data/<ns>/tags/kelvin/gas_type/}. */
public final class ModGasTags {

    private ModGasTags() {
    }

    /** Gases an entity can breathe; anywhere else it drowns as if underwater. Just air by default. */
    public static final TagKey<GasType> BREATHABLE =
            TagKey.create(GasTypeRegistry.INSTANCE.getGAS_TYPE_REGISTRY_KEY(), ZPSMod.resource("breathable"));

    /** The least share of a mix that must be {@link #BREATHABLE} for an entity to breathe it. */
    public static final double MIN_BREATHABLE_SHARE = 0.10;

    /**
     * Whether an entity can breathe a mix of {@code gases}, by weight: at least
     * {@link #MIN_BREATHABLE_SHARE} of it must be {@link #BREATHABLE}.
     */
    public static boolean isBreathable(Level level, Map<ResourceLocation, Double> gases) {
        double total = 0;
        double breathable = 0;
        for (Map.Entry<ResourceLocation, Double> gas : gases.entrySet()) {
            if (gas.getValue() > 0) {
                total += gas.getValue();
                if (isBreathable(level, gas.getKey())) {
                    breathable += gas.getValue();
                }
            }
        }
        return total > 0 && breathable >= MIN_BREATHABLE_SHARE * total;
    }

    /** Whether the gas {@code gas} names is {@link #BREATHABLE}; an id no mod registers is not. */
    public static boolean isBreathable(Level level, ResourceLocation gas) {
        return level.registryAccess().registryOrThrow(GasTypeRegistry.INSTANCE.getGAS_TYPE_REGISTRY_KEY())
                .getHolder(gas).map(holder -> holder.is(BREATHABLE)).orElse(false);
    }
}
