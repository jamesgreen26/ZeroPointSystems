package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/** Tags on Kelvin's {@code kelvin:gas_type} registry, under {@code data/<ns>/tags/kelvin/gas_type/}. */
public final class ModGasTags {

    private ModGasTags() {
    }

    /** Gases an entity can breathe; anywhere else it drowns as if underwater. Just air by default. */
    public static final TagKey<GasType> BREATHABLE =
            TagKey.create(GasTypeRegistry.INSTANCE.getGAS_TYPE_REGISTRY_KEY(), ZPSMod.resource("breathable"));

    /** Whether the gas {@code gas} names is {@link #BREATHABLE}; an id no mod registers is not. */
    public static boolean isBreathable(Level level, ResourceLocation gas) {
        return level.registryAccess().registryOrThrow(GasTypeRegistry.INSTANCE.getGAS_TYPE_REGISTRY_KEY())
                .getHolder(gas).map(holder -> holder.is(BREATHABLE)).orElse(false);
    }
}
