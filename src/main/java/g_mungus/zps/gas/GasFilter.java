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
 * Which gases a connection holds back: a blacklist of gas ids, everything else passing. Gases are
 * held by id rather than {@link GasType} so a filter naming a gas from a mod that is later
 * removed survives the round trip through NBT.
 *
 * @param blocked the ids of the gases that do not pass
 */
public record GasFilter(Set<ResourceLocation> blocked) {

    /** Nothing is held back. */
    public static final GasFilter PASS_ALL = new GasFilter(Set.of());

    /** The most gases a filter will hold, so a stray packet cannot fill a block entity. */
    public static final int MAX_GASES = 64;

    private static final String GASES_TAG = "Gases";

    public GasFilter {
        blocked = Set.copyOf(blocked);
    }

    public boolean passes(GasType gas) {
        return !blocked.contains(gas.getResourceLocation());
    }

    public boolean blocks(ResourceLocation gas) {
        return blocked.contains(gas);
    }

    /** The blocked gases that are actually registered, as Kelvin's filtered edges want them. */
    public HashSet<GasType> resolve() {
        HashSet<GasType> resolved = new HashSet<>();
        for (ResourceLocation id : blocked) {
            GasType gas = GasTypeRegistry.INSTANCE.getGAS_TYPES().get(id);
            if (gas != null) {
                resolved.add(gas);
            }
        }
        return resolved;
    }

    public GasFilter toggling(ResourceLocation gas) {
        Set<ResourceLocation> updated = new HashSet<>(blocked);
        if (!updated.remove(gas)) {
            updated.add(gas);
        }
        return new GasFilter(updated);
    }

    // --- persistence and wire format --------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ResourceLocation gas : blocked) {
            list.add(StringTag.valueOf(gas.toString()));
        }
        tag.put(GASES_TAG, list);
        return tag;
    }

    public static GasFilter load(CompoundTag tag) {
        Set<ResourceLocation> blocked = new HashSet<>();
        for (Tag entry : tag.getList(GASES_TAG, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                blocked.add(id);
            }
        }
        return new GasFilter(blocked);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeCollection(blocked, FriendlyByteBuf::writeResourceLocation);
    }

    public static GasFilter read(FriendlyByteBuf buffer) {
        return new GasFilter(buffer.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation));
    }
}
