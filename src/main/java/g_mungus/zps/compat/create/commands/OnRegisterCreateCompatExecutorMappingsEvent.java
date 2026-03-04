package g_mungus.zps.compat.create.commands;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

public class OnRegisterCreateCompatExecutorMappingsEvent extends Event {
    private final Multimap<ResourceLocation, MappingEntry> mappings = HashMultimap.create();

    public void accept(MappingEntry... entries) {
        for (MappingEntry entry : entries) {
            mappings.put(entry.block(), entry);
        }
    }

    public String resolve(ResourceLocation block, String defaultName) {
        for (MappingEntry entry : mappings.get(block)) {
            if (entry.oldName().equals(defaultName)) return entry.newName();
        }
        return defaultName;
    }
}
