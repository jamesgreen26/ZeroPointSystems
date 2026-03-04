package g_mungus.zps.compat.create.commands;

import net.minecraft.resources.ResourceLocation;

public record MappingEntry(
        ResourceLocation block,
        String oldName,
        String newName
) {
    public MappingEntry(String block, String oldName, String newName) {
        this(ResourceLocation.parse(block), oldName, newName);
    }
}