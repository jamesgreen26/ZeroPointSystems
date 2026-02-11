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


@ApiStatus.Internal
public class IdentityObject2IntMap extends AbstractObject2IntMap<CommandNode<SharedSuggestionProvider>> {
    /**
         * Wrapper that provides identity-based equality and hashcode for CommandNode keys.
         * This ensures nodes are compared by object identity (==) rather than equals().
         */
        private record IdentityWrapper(CommandNode<SharedSuggestionProvider> node) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IdentityWrapper other)) {
                return false;
            }
            return this.node.equals(other.node)
                    && Objects.equals(this.node.getRedirect(), other.node.getRedirect());
        }

        @Override
        public int hashCode() {
            CommandNode<SharedSuggestionProvider> redirect = this.node.getRedirect();
            if (redirect == null) {
                return this.node.hashCode();
            } else {
                return Objects.hash(
                        this.node, redirect
                );
            }
        }
    }

    private final java.util.HashMap<IdentityWrapper, Integer> map;

    public IdentityObject2IntMap() {
        this.map = new java.util.HashMap<>();
    }

    @Override
    public int getInt(Object key) {
        if (!(key instanceof CommandNode<?> node)) {
            return defaultReturnValue();
        }
        @SuppressWarnings("unchecked")
        CommandNode<SharedSuggestionProvider> typedNode = (CommandNode<SharedSuggestionProvider>) node;
        Integer value = map.get(new IdentityWrapper(typedNode));
        return value != null ? value : defaultReturnValue();
    }

    @Override
    public int put(CommandNode<SharedSuggestionProvider> key, int value) {
        Integer oldValue = map.put(new IdentityWrapper(key), value);
        return oldValue != null ? oldValue : defaultReturnValue();
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof CommandNode<?> node)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        CommandNode<SharedSuggestionProvider> typedNode = (CommandNode<SharedSuggestionProvider>) node;
        return map.containsKey(new IdentityWrapper(typedNode));
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
                Iterator<java.util.Map.Entry<IdentityWrapper, Integer>> wrappedIterator = map.entrySet().iterator();

                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return wrappedIterator.hasNext();
                    }

                    @Override
                    public Entry<CommandNode<SharedSuggestionProvider>> next() {
                        java.util.Map.Entry<IdentityWrapper, Integer> wrappedEntry = wrappedIterator.next();
                        // Unwrap the IdentityWrapper to get the original CommandNode
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