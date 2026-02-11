package g_mungus.zps.mixin;

import com.google.common.collect.Queues;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.zps.commands.api_impl.util.RedirectAwareObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Queue;

@Mixin(ClientboundCommandsPacket.class)
public class ClientboundCommandsPacketMixin {

    /**
     * @author ZeroPointSystems
     * @reason Fix command node deduplication bug where nodes with the same name but different
     *         redirect targets were incorrectly treated as identical during serialization.
     *
     *         <p>The original implementation used Object2IntOpenHashMap which relies on
     *         {@link CommandNode#equals} and {@link CommandNode#hashCode}. These methods only
     *         compare node names, ignoring redirect targets. This caused nodes like:
     *         <ul>
     *           <li>Node "alias" redirecting to "target1"</li>
     *           <li>Node "alias" redirecting to "target2"</li>
     *         </ul>
     *         to be treated as duplicates, with one overwriting the other in the map.
     *
     *         <p>The fix uses {@link RedirectAwareObject2IntMap} which compares both the node
     *         name AND redirect target, preserving all distinct nodes while still allowing
     *         beneficial deduplication of true duplicates (same name + same redirect).
     */
    @Overwrite
    private static Object2IntMap<CommandNode<SharedSuggestionProvider>> enumerateNodes(RootCommandNode<SharedSuggestionProvider> rootCommandNode) {
        // Use redirect-aware map to prevent incorrect deduplication
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2IntMap = new RedirectAwareObject2IntMap();

        Queue<CommandNode<SharedSuggestionProvider>> queue = Queues.newArrayDeque();
        queue.add(rootCommandNode);

        CommandNode<SharedSuggestionProvider> commandNode;
        while ((commandNode = queue.poll()) != null) {
            // containsKey and put now use redirect-aware comparison
            if (!object2IntMap.containsKey(commandNode)) {
                int i = object2IntMap.size();
                object2IntMap.put(commandNode, i);
                queue.addAll(commandNode.getChildren());
                if (commandNode.getRedirect() != null) {
                    queue.add(commandNode.getRedirect());
                }
            }
        }

        return object2IntMap;
    }
}
