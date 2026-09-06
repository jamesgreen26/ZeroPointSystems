package g_mungus.zps.client.model.connected;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public interface ConnectionRule {
    /**
     * @param self      the state of the block being rendered
     * @param neighbour the state of the neighbour being tested
     * @param face      the world direction of the face being drawn; the neighbour is in that face's plane
     */
    boolean matches(BlockState self, BlockState neighbour, Direction face);

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

    static boolean anyMatch(List<ConnectionRule> rules, BlockState self, BlockState neighbour, Direction face) {
        for (ConnectionRule rule : rules) {
            if (rule.matches(self, neighbour, face)) {
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
            case "match_block_face": {
                ResourceLocation block = ResourceLocation.parse(GsonHelper.getAsString(object, "block"));
                String property = GsonHelper.getAsString(object, "property", "facing");
                String side = GsonHelper.getAsString(object, "side", "same");
                if (!"same".equals(side) && !"opposite".equals(side)) {
                    throw new JsonParseException("match_block_face side must be \"same\" or \"opposite\", was: " + side);
                }
                return new MatchBlockFace(block, property, "opposite".equals(side));
            }
            default:
                throw new JsonParseException("Unknown connection type: " + type);
        }
    }

    /** Connects to neighbours of the same block as this one. */
    record SameBlock() implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour, Direction face) {
            return neighbour.is(self.getBlock());
        }
    }

    /** Connects to a specific block, ignoring its state. */
    record MatchBlock(ResourceLocation id) implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour, Direction face) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return block != Blocks.AIR && neighbour.is(block);
        }
    }

    /** Connects to a specific block only when the named state properties match the given values. */
    record MatchState(ResourceLocation id, Map<String, String> properties) implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour, Direction face) {
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

    /**
     * Connects to a specific block only on the face of this block that lines up with one particular face of
     * the neighbour, named by a direction property. With {@code opposite} the neighbour's chosen face is the
     * one behind the property's direction — e.g. a fuel injector's back, opposite its outer {@code facing}.
     * A neighbour whose chosen face is not coplanar with the face being drawn does not connect.
     */
    record MatchBlockFace(ResourceLocation id, String property, boolean opposite) implements ConnectionRule {
        @Override
        public boolean matches(BlockState self, BlockState neighbour, Direction face) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == Blocks.AIR || !neighbour.is(block)) {
                return false;
            }
            Property<?> property = neighbour.getBlock().getStateDefinition().getProperty(this.property);
            if (!(property instanceof DirectionProperty directions)) {
                return false;
            }
            Direction side = neighbour.getValue(directions);
            return (opposite ? side.getOpposite() : side) == face;
        }
    }
}
