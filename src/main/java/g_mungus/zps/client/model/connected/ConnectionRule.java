package g_mungus.zps.client.model.connected;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public interface ConnectionRule {
    boolean matches(BlockState self, BlockState neighbour);

    /** Parses the rule list. An empty/absent list defaults to {@link SameBlock} (connect to identical blocks). */
    static List<ConnectionRule> parse(@Nullable JsonArray array) {
        List<ConnectionRule> rules = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                rules.add(parseOne(GsonHelper.convertToJsonObject(element, "connection")));
            }
        }
        if (rules.isEmpty()) {
            rules.add(new SameBlock());
        }
        return rules;
    }

    static boolean anyMatch(List<ConnectionRule> rules, BlockState self, BlockState neighbour) {
        for (ConnectionRule rule : rules) {
            if (rule.matches(self, neighbour)) {
                return true;
            }
        }
        return false;
    }

    private static ConnectionRule parseOne(JsonObject object) {
        String type = GsonHelper.getAsString(object, "type");
        switch (type) {
            case "is_same_block":
                return new SameBlock();
            case "match_block":
                return new MatchBlock(ResourceLocation.parse(GsonHelper.getAsString(object, "block")));
            case "match_state": {
                ResourceLocation block = ResourceLocation.parse(GsonHelper.getAsString(object, "block"));
                Map<String, String> properties = new LinkedHashMap<>();
                if (object.has("properties")) {
                    for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(object, "properties").entrySet()) {
                        properties.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return new MatchState(block, properties);
            }
            default:
                throw new JsonParseException("Unknown connection type: " + type);
        }
    }

    /** Connects to neighbours of the same block as this one. */
    record SameBlock() implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour) {
            return neighbour.is(self.getBlock());
        }
    }

    /** Connects to a specific block, ignoring its state. */
    record MatchBlock(ResourceLocation id) implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return block != Blocks.AIR && neighbour.is(block);
        }
    }

    /** Connects to a specific block only when the named state properties match the given values. */
    record MatchState(ResourceLocation id, Map<String, String> properties) implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == Blocks.AIR || !neighbour.is(block)) {
                return false;
            }
            StateDefinition<Block, BlockState> definition = neighbour.getBlock().getStateDefinition();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = definition.getProperty(entry.getKey());
                if (property == null || !matchProperty(neighbour, property, entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static <T extends Comparable<T>> boolean matchProperty(BlockState state, Property<T> property, String value) {
            return property.getValue(value).map(parsed -> state.getValue(property).equals(parsed)).orElse(false);
        }
    }
}
