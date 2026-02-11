package g_mungus.zps.mixin;

import com.google.common.collect.Queues;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.zps.commands.api_impl.util.IdentityObject2IntMap;
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
     *         parents and redirects were incorrectly treated as identical. This caused redirect
     *         targets to be lost during serialization. The fix uses identity-based comparison
     *         instead of equals/hashCode to ensure distinct nodes are not deduplicated.
     */
    @Overwrite
    private static Object2IntMap<CommandNode<SharedSuggestionProvider>> enumerateNodes(RootCommandNode<SharedSuggestionProvider> rootCommandNode) {
        // Use custom identity-based map to prevent incorrect deduplication
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2IntMap = new IdentityObject2IntMap();

        Queue<CommandNode<SharedSuggestionProvider>> queue = Queues.newArrayDeque();
        queue.add(rootCommandNode);

        CommandNode<SharedSuggestionProvider> commandNode;
        while ((commandNode = queue.poll()) != null) {
            // Now containsKey and put both use identity comparison
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
