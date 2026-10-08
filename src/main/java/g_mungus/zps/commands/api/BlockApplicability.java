package g_mungus.zps.commands.api;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The blocks a script node is for. These also make the "works with" lists.
 *
 * @param entries block ids ({@code "zps:gas_gauge"}) or, with a leading {@code #}, block tags
 *                ({@code "#zps:reactor_wall"}), the way a datapack would write them
 */
public record BlockApplicability(Set<String> entries) implements TargetApplicability {

    private static final String TAG_PREFIX = "#";

    /** Sent as the blocks it names when sent, tags opened, since the client may not have the same tags. */
    public static final Type<BlockApplicability> TYPE = new Type<>(ZPSMod.resource("blocks"),
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(BlockApplicability::ofBlocks, blocks -> List.copyOf(blocks.resolve())));

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

    @Override
    public boolean appliesTo(Level level, ScriptTarget target) {
        return appliesTo(target.block());
    }

    @Override
    public Type<?> type() {
        return TYPE;
    }

    public boolean appliesToAny(Collection<ResourceLocation> blocks) {
        Set<ResourceLocation> resolved = resolve();
        return blocks.stream().anyMatch(resolved::contains);
    }
}
