package g_mungus.zps.commands.content;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.content.executors.DimensionIndexCommand;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Consumer;

/**
 * Mappers for ZPS's own types, and reading those types back out of text. The arithmetic,
 * comparisons and text handling on {@code int}, {@code double}, {@code string} and
 * {@code boolean} come with the script engine, as do {@code ==} and {@code as_string} for every
 * type that can be written in a script.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class ZPSScriptMappers {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        // Everything here but what reads the server or a registry predicate only computes, so a phys tick
        // program may run it in the physics step.
        Consumer<ScriptNode> safe = event::registerPhysTickSafe;

        // Block positions
        safe.accept(ZPSNodes.mapper("x", ZPSScriptTypes.BLOCK_POS, BuiltInTypes.INT, (pos, context) -> pos.getX()));
        safe.accept(ZPSNodes.mapper("y", ZPSScriptTypes.BLOCK_POS, BuiltInTypes.INT, (pos, context) -> pos.getY()));
        safe.accept(ZPSNodes.mapper("z", ZPSScriptTypes.BLOCK_POS, BuiltInTypes.INT, (pos, context) -> pos.getZ()));
        safe.accept(ZPSNodes.mapper("center", ZPSScriptTypes.BLOCK_POS, ZPSScriptTypes.VEC_POS,
                (pos, context) -> pos.getCenter()));
        safe.accept(ZPSNodes.argumentMapper("offset_x", ZPSScriptTypes.BLOCK_POS, ZPSScriptTypes.BLOCK_POS, "int",
                BuiltInTypes.INT, (pos, offset, context) -> pos.offset(offset, 0, 0)));
        safe.accept(ZPSNodes.argumentMapper("offset_y", ZPSScriptTypes.BLOCK_POS, ZPSScriptTypes.BLOCK_POS, "int",
                BuiltInTypes.INT, (pos, offset, context) -> pos.offset(0, offset, 0)));
        safe.accept(ZPSNodes.argumentMapper("offset_z", ZPSScriptTypes.BLOCK_POS, ZPSScriptTypes.BLOCK_POS, "int",
                BuiltInTypes.INT, (pos, offset, context) -> pos.offset(0, 0, offset)));
        safe.accept(ZPSNodes.argumentMapper("offset", ZPSScriptTypes.BLOCK_POS, ZPSScriptTypes.BLOCK_POS,
                "coordinates", ZPSScriptTypes.BLOCK_POS, (pos, offset, context) -> pos.offset(offset)));

        // Positions
        safe.accept(ZPSNodes.mapper("x", ZPSScriptTypes.VEC_POS, BuiltInTypes.DOUBLE, (vec, context) -> vec.x));
        safe.accept(ZPSNodes.mapper("y", ZPSScriptTypes.VEC_POS, BuiltInTypes.DOUBLE, (vec, context) -> vec.y));
        safe.accept(ZPSNodes.mapper("z", ZPSScriptTypes.VEC_POS, BuiltInTypes.DOUBLE, (vec, context) -> vec.z));
        safe.accept(ZPSNodes.argumentMapper("distance_to", ZPSScriptTypes.VEC_POS, BuiltInTypes.DOUBLE,
                "coordinates", ZPSScriptTypes.VEC_POS, (vec, other, context) -> vec.distanceTo(other)));
        safe.accept(ZPSNodes.argumentMapper("direction_to", ZPSScriptTypes.VEC_POS, ZPSScriptTypes.VEC_DIR,
                "coordinates", ZPSScriptTypes.VEC_POS, (vec, other, context) -> other.subtract(vec).normalize()));
        safe.accept(ZPSNodes.mapper("rounded_down", ZPSScriptTypes.VEC_POS, ZPSScriptTypes.BLOCK_POS,
                (vec, context) -> BlockPos.containing(vec)));

        // Sizes
        safe.accept(ZPSNodes.mapper("x", ZPSScriptTypes.VEC_BOX, BuiltInTypes.DOUBLE, (vec, context) -> vec.x));
        safe.accept(ZPSNodes.mapper("y", ZPSScriptTypes.VEC_BOX, BuiltInTypes.DOUBLE, (vec, context) -> vec.y));
        safe.accept(ZPSNodes.mapper("z", ZPSScriptTypes.VEC_BOX, BuiltInTypes.DOUBLE, (vec, context) -> vec.z));
        safe.accept(ZPSNodes.mapper("volume", ZPSScriptTypes.VEC_BOX, BuiltInTypes.DOUBLE,
                (vec, context) -> vec.x * vec.y * vec.z));

        // Directions
        safe.accept(ZPSNodes.mapper("x", ZPSScriptTypes.VEC_DIR, BuiltInTypes.DOUBLE, (vec, context) -> vec.x));
        safe.accept(ZPSNodes.mapper("y", ZPSScriptTypes.VEC_DIR, BuiltInTypes.DOUBLE, (vec, context) -> vec.y));
        safe.accept(ZPSNodes.mapper("z", ZPSScriptTypes.VEC_DIR, BuiltInTypes.DOUBLE, (vec, context) -> vec.z));
        safe.accept(ZPSNodes.mapper("length", ZPSScriptTypes.VEC_DIR, BuiltInTypes.DOUBLE,
                (vec, context) -> vec.length()));
        safe.accept(ZPSNodes.mapper("normalize", ZPSScriptTypes.VEC_DIR, ZPSScriptTypes.VEC_DIR,
                (vec, context) -> vec.normalize()));
        safe.accept(ZPSNodes.argumentMapper("cross", ZPSScriptTypes.VEC_DIR, ZPSScriptTypes.VEC_DIR, "direction",
                ZPSScriptTypes.VEC_DIR, (vec, other, context) -> vec.cross(other)));
        safe.accept(ZPSNodes.argumentMapper("dot", ZPSScriptTypes.VEC_DIR, BuiltInTypes.DOUBLE, "direction",
                ZPSScriptTypes.VEC_DIR, (vec, other, context) -> vec.dot(other)));

        // Blocks and items, matched the way /execute if block and /clear match them
        event.register(ZPSNodes.rawArgumentMapper("==", ZPSScriptTypes.BLOCK_STATE, BuiltInTypes.BOOLEAN, "block",
                BlockPredicateArgument.blockPredicate(event.buildContext()), BlockPredicateArgument.Result.class,
                (state, predicate, context) -> predicate.test(blockInWorld(state))));
        event.register(ZPSNodes.rawArgumentMapper("==", ZPSScriptTypes.ITEM, BuiltInTypes.BOOLEAN, "item",
                ItemPredicateArgument.itemPredicate(event.buildContext()), ItemPredicateArgument.Result.class,
                (stack, predicate, context) -> predicate.test(stack)));
        safe.accept(ZPSNodes.mapper("count", ZPSScriptTypes.ITEM, BuiltInTypes.INT,
                (stack, context) -> stack.getCount()));
        // Ids as text, to compare against strings a script reads or receives, which == cannot take
        safe.accept(ZPSNodes.mapper("id", ZPSScriptTypes.BLOCK_STATE, BuiltInTypes.STRING,
                (state, context) -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        safe.accept(ZPSNodes.mapper("id", ZPSScriptTypes.ITEM, BuiltInTypes.STRING,
                (stack, context) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));

        // Dimensions
        event.register(ZPSNodes.mapper("index", ZPSScriptTypes.DIMENSION, BuiltInTypes.INT,
                (dimension, context) -> DimensionIndexCommand.getIndex(context.level().getServer(),
                        ResourceLocation.parse(dimension))));

        // Reading values out of text
        safe.accept(ZPSNodes.mapper("as_int", BuiltInTypes.STRING, BuiltInTypes.INT,
                (text, context) -> Integer.parseInt(text)));
        safe.accept(ZPSNodes.mapper("as_double", BuiltInTypes.STRING, BuiltInTypes.DOUBLE,
                (text, context) -> Double.parseDouble(text)));
        safe.accept(ZPSNodes.mapper("as_boolean", BuiltInTypes.STRING, BuiltInTypes.BOOLEAN,
                (text, context) -> parseBoolean(text)));
        safe.accept(ZPSNodes.mapper("as_block_pos", BuiltInTypes.STRING, ZPSScriptTypes.BLOCK_POS,
                (text, context) -> {
                    String[] parts = text.split(" ");
                    return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }));
        safe.accept(ZPSNodes.mapper("as_dimension", BuiltInTypes.STRING, ZPSScriptTypes.DIMENSION,
                (text, context) -> text));

        // Writing a character by its code, such as one pixel of a text display's video
        safe.accept(ZPSNodes.mapper("as_char", BuiltInTypes.INT, BuiltInTypes.STRING,
                (code, context) -> charOf(code)));
    }

    /** A block that is {@code state}, as a block predicate tests one, with no world behind it. */
    private static BlockInWorld blockInWorld(BlockState state) {
        @SuppressWarnings("ConstantConditions")
        BlockInWorld block = new BlockInWorld(null, new BlockPos(0, 0, 0), false);
        BlockInWorldMutable mutable = (BlockInWorldMutable) block;
        mutable.zps$setState(state);
        mutable.zps$setCachedEntity(true);
        return block;
    }

    public interface BlockInWorldMutable {
        void zps$setState(BlockState state);
        void zps$setCachedEntity(boolean b);
    }

    /**
     * The character with Unicode code point {@code code}, as text: 65 is {@code A}. A code no
     * character has gives a space, so a script drawing a video row never stops on a bad value.
     */
    private static String charOf(int code) {
        return Character.isValidCodePoint(code) ? Character.toString(code) : " ";
    }

    private static boolean parseBoolean(String value) {
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Expected \"true\" or \"false\", got \"" + value + "\"");
    }
}
