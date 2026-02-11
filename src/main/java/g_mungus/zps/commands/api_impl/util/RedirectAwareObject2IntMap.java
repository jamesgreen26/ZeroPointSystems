package g_mungus.zps.commands.api_impl.util;

import com.mojang.brigadier.tree.CommandNode;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Objects;


/**
 * A specialized Object2IntMap for CommandNode keys that uses redirect-aware equality.
 *
 * <p>Unlike the default HashMap behavior which only compares node names via {@link CommandNode#equals},
 * this map considers both the node name AND its redirect target when determining equality.
 * This prevents incorrect deduplication of nodes that have the same name but point to different
 * redirect targets.
 *
 * <p>For example, two nodes both named "alias" but redirecting to different targets will be
 * treated as distinct keys, whereas the default CommandNode.equals() would treat them as identical.
 *
 * <p><strong>Internal use only.</strong> This class is specifically designed to fix command tree
 * serialization bugs and should not be used outside that context.
 */
@ApiStatus.Internal
public class RedirectAwareObject2IntMap extends AbstractObject2IntMap<CommandNode<SharedSuggestionProvider>> {
    /**
     * Wrapper that provides redirect-aware equality and hashCode for CommandNode keys.
     *
     * <p>Two wrapped nodes are considered equal if and only if:
     * <ul>
     *   <li>Their node names match (via {@link CommandNode#equals})</li>
     *   <li>AND their redirect targets are the same object (via {@link Objects#equals})</li>
     * </ul>
     *
     * <p>This allows beneficial deduplication (nodes with same name and same redirect)
     * while preventing incorrect deduplication (nodes with same name but different redirects).
     */
    private record RedirectAwareWrapper(CommandNode<SharedSuggestionProvider> node) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RedirectAwareWrapper other)) {
                return false;
            }
            // Check both node name (via CommandNode.equals) AND redirect target
            return this.node.equals(other.node)
                    && Objects.equals(this.node.getRedirect(), other.node.getRedirect());
        }

        @Override
        public int hashCode() {
            CommandNode<SharedSuggestionProvider> redirect = this.node.getRedirect();
            if (redirect == null) {
                return this.node.hashCode();
            } else {
                // Include redirect in hash to match equals() contract
                return Objects.hash(
                        this.node, redirect
                );
            }
        }
    }

    private final java.util.HashMap<RedirectAwareWrapper, Integer> map;

    public RedirectAwareObject2IntMap() {
        this.map = new java.util.HashMap<>();
    }

    @Override
    public int getInt(Object key) {
        if (!(key instanceof CommandNode<?> node)) {
            return defaultReturnValue();
        }
        @SuppressWarnings("unchecked")
        CommandNode<SharedSuggestionProvider> typedNode = (CommandNode<SharedSuggestionProvider>) node;
        Integer value = map.get(new RedirectAwareWrapper(typedNode));
        return value != null ? value : defaultReturnValue();
    }

    @Override
    public int put(CommandNode<SharedSuggestionProvider> key, int value) {
        Integer oldValue = map.put(new RedirectAwareWrapper(key), value);
        return oldValue != null ? oldValue : defaultReturnValue();
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof CommandNode<?> node)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        CommandNode<SharedSuggestionProvider> typedNode = (CommandNode<SharedSuggestionProvider>) node;
        return map.containsKey(new RedirectAwareWrapper(typedNode));
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public ObjectSet<Entry<CommandNode<SharedSuggestionProvider>>> object2IntEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NotNull ObjectIterator<Entry<CommandNode<SharedSuggestionProvider>>> iterator() {
                Iterator<java.util.Map.Entry<RedirectAwareWrapper, Integer>> wrappedIterator = map.entrySet().iterator();

                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return wrappedIterator.hasNext();
                    }

                    @Override
                    public Entry<CommandNode<SharedSuggestionProvider>> next() {
                        java.util.Map.Entry<RedirectAwareWrapper, Integer> wrappedEntry = wrappedIterator.next();
                        // Unwrap to get the original CommandNode
                        return new BasicEntry<>(wrappedEntry.getKey().node(), wrappedEntry.getValue());
                    }

                    @Override
                    public void remove() {
                        wrappedIterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }
}
