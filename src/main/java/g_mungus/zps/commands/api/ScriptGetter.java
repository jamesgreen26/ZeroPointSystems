package g_mungus.zps.commands.api;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * A value a script can read off the block its bus faces.
 *
 * <p>A getter can be tied to the blocks it makes sense for; one tied to none applies everywhere.
 * The restriction only steers what is offered — suggestions and the "works with" lists — so the
 * function still has to cope with being pointed at anything.
 *
 * @param associatedBlocks the blocks the getter is for, or null for all of them. Each entry is a
 *                         block id ({@code "zps:gas_gauge"}) or, with a leading {@code #}, a block
 *                         tag ({@code "#zps:reactor_wall"}), the way a datapack would write them.
 *                         Read it through {@link #resolveAssociatedBlocks()}, which opens the tags.
 */
public record ScriptGetter<O>(
        String displayName,
        Class<O> outputType,
        ResourceLocation outputKey,
        Function<ScriptContext, O> function,
        @Nullable Set<String> associatedBlocks
) implements ScriptNode {

    private static final String TAG_PREFIX = "#";

    /** Entries are parsed up front, so a malformed one fails at registration and not mid-game. */
    public ScriptGetter {
        if (associatedBlocks != null) {
            associatedBlocks = Set.copyOf(associatedBlocks);
            for (String entry : associatedBlocks) {
                ResourceLocation.parse(entry.startsWith(TAG_PREFIX) ? entry.substring(TAG_PREFIX.length()) : entry);
            }
        }
    }

    public static <O> ScriptGetter<O> withBlocks(
            String displayName,
            Class<O> outputType,
            ResourceLocation outputKey,
            Function<ScriptContext, O> function,
            Set<String> associatedBlocks
    ) {
        return new ScriptGetter<>(displayName, outputType, outputKey, function, associatedBlocks);
    }

    /**
     * Every block the getter is for: the ids it names plus whatever its tags hold right now. Null
     * when it is unrestricted. Tags are read as they stand, so this is only meaningful once they
     * are loaded — on a running server, or a client that has joined one.
     */
    public @Nullable Set<ResourceLocation> resolveAssociatedBlocks() {
        if (associatedBlocks == null) {
            return null;
        }
        Set<ResourceLocation> resolved = new LinkedHashSet<>();
        for (String entry : associatedBlocks) {
            if (!entry.startsWith(TAG_PREFIX)) {
                resolved.add(ResourceLocation.parse(entry));
                continue;
            }
            TagKey<Block> tag = TagKey.create(Registries.BLOCK,
                    ResourceLocation.parse(entry.substring(TAG_PREFIX.length())));
            BuiltInRegistries.BLOCK.getTag(tag).ifPresent(members -> {
                for (Holder<Block> member : members) {
                    resolved.add(BuiltInRegistries.BLOCK.getKey(member.value()));
                }
            });
        }
        return resolved;
    }

    /** Whether the getter should be offered when these are the blocks in reach. */
    public boolean appliesToAny(Collection<ResourceLocation> blocks) {
        Set<ResourceLocation> resolved = resolveAssociatedBlocks();
        return resolved == null || blocks.stream().anyMatch(resolved::contains);
    }
}
