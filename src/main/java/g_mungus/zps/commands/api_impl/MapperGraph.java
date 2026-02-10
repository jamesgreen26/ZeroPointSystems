package g_mungus.zps.commands.api_impl;

import g_mungus.zps.commands.api.ScriptMapper;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class MapperGraph {

    private final Map<ResourceLocation, Set<ScriptMapper<?, ?>>> reverseIndex = new HashMap<>();

    // Cached transitive closure
    private final Map<ResourceLocation, Set<ScriptMapper<?, ?>>> closureCache = new HashMap<>();
    private boolean dirty = false;

    public void addMapper(ScriptMapper<?, ?> mapper) {
        reverseIndex
                .computeIfAbsent(mapper.outputKey(), k -> new HashSet<>())
                .add(mapper);
        dirty = true;
    }

    private void rebuildCache() {
        closureCache.clear();

        for (ResourceLocation output : reverseIndex.keySet()) {
            closureCache.put(output, computeClosureFor(output));
        }

        dirty = false;
    }


    private Set<ScriptMapper<?, ?>> computeClosureFor(ResourceLocation desiredOutput) {
        Set<ScriptMapper<?, ?>> result = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        Deque<ResourceLocation> stack = new ArrayDeque<>();

        stack.push(desiredOutput);

        while (!stack.isEmpty()) {
            ResourceLocation current = stack.pop();
            if (!visited.add(current)) continue;

            Set<ScriptMapper<?, ?>> producers = reverseIndex.get(current);
            if (producers == null) continue;

            for (ScriptMapper<?, ?> mapper : producers) {
                if (result.add(mapper)) {
                    stack.push(mapper.inputKey());
                }
            }
        }

        return Set.copyOf(result); // immutable for safety
    }

    public Set<ScriptMapper<?, ?>> findAllMappersLeadingTo(ResourceLocation desiredOutput) {
        if (dirty) {
            rebuildCache();
        }
        return closureCache.getOrDefault(desiredOutput, Set.of());
    }

}
