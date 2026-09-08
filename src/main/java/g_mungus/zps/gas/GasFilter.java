package g_mungus.zps.gas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.HashSet;
import java.util.Set;

/**
 * Which gases a connection lets through: a set of gas ids read as a blacklist (everything but
 * these) or a whitelist (only these). Gases are held by id rather than {@link GasType} so a
 * filter set for a gas from a mod that is later removed survives the round trip through NBT.
 *
 * @param blacklist true to pass every gas except {@code gases}, false to pass only {@code gases}
 * @param gases     the gas ids the filter names
 */
public record GasFilter(boolean blacklist, Set<ResourceLocation> gases) {

    /** An empty blacklist: nothing is held back. */
    public static final GasFilter PASS_ALL = new GasFilter(true, Set.of());

    /** The most gases a filter will hold, so a stray packet cannot fill a block entity. */
    public static final int MAX_GASES = 64;

    private static final String BLACKLIST_TAG = "Blacklist";
    private static final String GASES_TAG = "Gases";

    public GasFilter {
        gases = Set.copyOf(gases);
    }

    public boolean passes(GasType gas) {
        return blacklist != gases.contains(gas.getResourceLocation());
    }

    /** The named gases that are actually registered, as Kelvin's filtered edges want them. */
    public HashSet<GasType> resolve() {
        HashSet<GasType> resolved = new HashSet<>();
        for (ResourceLocation id : gases) {
            GasType gas = GasTypeRegistry.INSTANCE.getGAS_TYPES().get(id);
            if (gas != null) {
                resolved.add(gas);
            }
        }
        return resolved;
    }

    public GasFilter withBlacklist(boolean blacklist) {
        return new GasFilter(blacklist, gases);
    }

    public GasFilter toggling(ResourceLocation gas) {
        Set<ResourceLocation> updated = new HashSet<>(gases);
        if (!updated.remove(gas)) {
            updated.add(gas);
        }
        return new GasFilter(blacklist, updated);
    }

    // --- persistence and wire format --------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(BLACKLIST_TAG, blacklist);
        ListTag list = new ListTag();
        for (ResourceLocation gas : gases) {
            list.add(StringTag.valueOf(gas.toString()));
        }
        tag.put(GASES_TAG, list);
        return tag;
    }

    public static GasFilter load(CompoundTag tag) {
        if (!tag.contains(BLACKLIST_TAG)) {
            return PASS_ALL;
        }
        Set<ResourceLocation> gases = new HashSet<>();
        for (Tag entry : tag.getList(GASES_TAG, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                gases.add(id);
            }
        }
        return new GasFilter(tag.getBoolean(BLACKLIST_TAG), gases);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(blacklist);
        buffer.writeCollection(gases, FriendlyByteBuf::writeResourceLocation);
    }

    public static GasFilter read(FriendlyByteBuf buffer) {
        boolean blacklist = buffer.readBoolean();
        Set<ResourceLocation> gases = buffer.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        return new GasFilter(blacklist, gases);
    }
}
