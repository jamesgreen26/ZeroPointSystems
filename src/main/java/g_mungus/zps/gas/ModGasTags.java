package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tags on Kelvin's gas types, under {@code data/<ns>/tags/kelvin/gas_type/}. Kelvin keeps no
 * Minecraft registry on 1.20.1, so these are read here with vanilla's tag loader rather than
 * being registry tags.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class ModGasTags {

    private ModGasTags() {
    }

    /** Gases an entity can breathe; anywhere else it drowns as if underwater. Just air by default. */
    public static final ResourceLocation BREATHABLE = ZPSMod.resource("breathable");

    private static volatile Map<ResourceLocation, Set<ResourceLocation>> tags = Map.of();

    /** Whether the gas {@code gas} names is {@link #BREATHABLE}; an id no mod registers is not. */
    public static boolean isBreathable(Level level, ResourceLocation gas) {
        return is(gas, BREATHABLE);
    }

    /** Whether the registered gas {@code gas} is in {@code tag}. */
    public static boolean is(ResourceLocation gas, ResourceLocation tag) {
        return tags.getOrDefault(tag, Set.of()).contains(gas);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Map<ResourceLocation, Set<ResourceLocation>>>() {
            @Override
            protected Map<ResourceLocation, Set<ResourceLocation>> prepare(ResourceManager resources, ProfilerFiller profiler) {
                TagLoader<ResourceLocation> loader = new TagLoader<>(ModGasTags::registered, "tags/kelvin/gas_type");
                Map<ResourceLocation, Set<ResourceLocation>> loaded = new HashMap<>();
                for (Map.Entry<ResourceLocation, Collection<ResourceLocation>> entry : loader.loadAndBuild(resources).entrySet()) {
                    loaded.put(entry.getKey(), Set.copyOf(new HashSet<>(entry.getValue())));
                }
                return loaded;
            }

            @Override
            protected void apply(Map<ResourceLocation, Set<ResourceLocation>> loaded, ResourceManager resources, ProfilerFiller profiler) {
                tags = Map.copyOf(loaded);
            }
        });
    }

    private static Optional<ResourceLocation> registered(ResourceLocation id) {
        GasType gas = GasTypeRegistry.INSTANCE.getGAS_TYPES().get(id);
        return gas == null ? Optional.empty() : Optional.of(id);
    }
}
