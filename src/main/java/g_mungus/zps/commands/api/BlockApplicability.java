package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.Applicability;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The blocks a script node is for. An executor among several of the same name runs for the block
 * it names; otherwise this only steers what is suggested and what the "works with" lists show, so
 * a node still has to cope with being pointed at anything.
 *
 * @param entries block ids ({@code "zps:gas_gauge"}) or, with a leading {@code #}, block tags
 *                ({@code "#zps:reactor_wall"}), the way a datapack would write them
 */
public record BlockApplicability(Set<String> entries) implements Applicability {

    private static final String TAG_PREFIX = "#";

    /** Entries are parsed up front, so a malformed one fails at registration and not mid-game. */
    public BlockApplicability {
        entries = Set.copyOf(entries);
        for (String entry : entries) {
            ResourceLocation.parse(entry.startsWith(TAG_PREFIX) ? entry.substring(TAG_PREFIX.length()) : entry);
        }
    }

    public static BlockApplicability of(String... entries) {
        return new BlockApplicability(Set.of(entries));
    }

    public static BlockApplicability ofBlocks(Collection<ResourceLocation> blocks) {
        Set<String> entries = new LinkedHashSet<>();
        blocks.forEach(block -> entries.add(block.toString()));
        return new BlockApplicability(entries);
    }

    /**
     * Every block this names: its ids plus whatever its tags hold right now. Tags are read as they
     * stand, so this is only meaningful once they are loaded.
     */
    public Set<ResourceLocation> resolve() {
        Set<ResourceLocation> resolved = new LinkedHashSet<>();
        for (String entry : entries) {
            if (!entry.startsWith(TAG_PREFIX)) {
                resolved.add(ResourceLocation.parse(entry));
                continue;
            }
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(entry.substring(TAG_PREFIX.length())));
            BuiltInRegistries.BLOCK.getTag(tag).ifPresent(members -> {
                for (Holder<Block> member : members) {
                    resolved.add(BuiltInRegistries.BLOCK.getKey(member.value()));
                }
            });
        }
        return resolved;
    }

    public boolean appliesTo(ResourceLocation block) {
        return resolve().contains(block);
    }

    public boolean appliesToAny(Collection<ResourceLocation> blocks) {
        Set<ResourceLocation> resolved = resolve();
        return blocks.stream().anyMatch(resolved::contains);
    }
}
