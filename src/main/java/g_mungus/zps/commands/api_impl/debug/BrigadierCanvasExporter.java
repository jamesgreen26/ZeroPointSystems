package g_mungus.zps.commands.api_impl.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import java.security.SecureRandom;
import java.util.*;

public class BrigadierCanvasExporter<S> {

    private static final int NODE_WIDTH = 250;
    private static final int NODE_HEIGHT = 60;

    private final Map<CommandNode<S>, String> nodeIds = new IdentityHashMap<>();
    private final List<Map<String, Object>> nodes = new ArrayList<>();
    private final List<Map<String, Object>> edges = new ArrayList<>();

    private final SecureRandom random = new SecureRandom();

    public String export(CommandNode<S> root) {
        layoutAndVisit(root, 0, new int[]{0});
        exportEdges(root, new HashSet<>());

        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("nodes", nodes);
        canvas.put("edges", edges);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(canvas);
    }

    // ------------------------------------------------------------
    // Layout + Node Export
    // ------------------------------------------------------------

    private void layoutAndVisit(CommandNode<S> node, int depth, int[] rowCounter) {
        if (nodeIds.containsKey(node)) return;

        String id = generateId();
        nodeIds.put(node, id);

        int x = depth * 400;
        int y = rowCounter[0] * 150;
        rowCounter[0]++;

        nodes.add(createTextNode(id, x, y, formatNodeText(node)));

        for (CommandNode<S> child : node.getChildren()) {
            layoutAndVisit(child, depth + 1, rowCounter);
        }

        if (node.getRedirect() != null) {
            layoutAndVisit(node.getRedirect(), depth + 1, rowCounter);
        }
    }

    private Map<String, Object> createTextNode(String id, int x, int y, String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("x", x);
        node.put("y", y);
        node.put("width", NODE_WIDTH);
        node.put("height", NODE_HEIGHT);
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    private String formatNodeText(CommandNode<S> node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getName());

        if (node.getRedirect() != null) {
            sb.append("\n→ redirects to: ").append(node.getRedirect().getName());
        }

        if (node.getCommand() != null) {
            sb.append("\n[executes]");
        }

        return sb.toString();
    }

    // ------------------------------------------------------------
    // Edge Export
    // ------------------------------------------------------------

    private void exportEdges(CommandNode<S> node, Set<CommandNode<S>> visited) {
        if (!visited.add(node)) return;

        for (CommandNode<S> child : node.getChildren()) {
            addEdge(node, child, false);
            exportEdges(child, visited);
        }

        if (node.getRedirect() != null) {
            addEdge(node, node.getRedirect(), true);
            exportEdges(node.getRedirect(), visited);
        }
    }

    private void addEdge(CommandNode<S> from, CommandNode<S> to, boolean redirect) {
        String fromId = nodeIds.get(from);
        String toId = nodeIds.get(to);

        if (fromId == null || toId == null) return;

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", generateId());
        edge.put("fromNode", fromId);
        edge.put("fromSide", "bottom");
        edge.put("toNode", toId);
        edge.put("toSide", "top");

        edges.add(edge);
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    private String generateId() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
