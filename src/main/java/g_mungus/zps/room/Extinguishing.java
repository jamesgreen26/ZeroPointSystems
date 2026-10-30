package g_mungus.zps.room;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.ZPSMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flames that datapacks declare, each with what it goes out into ({@link Burner#CONVERTED}). Every
 * file under {@code data/<ns>/zps/extinguishing/} is a map of block state to block state:
 *
 * <pre>{@code
 * {
 *   "minecraft:torch": "zps:extinguished_torch",
 *   "somemod:brazier[lit=true]": "somemod:brazier[lit=false]"
 * }
 * }</pre>
 *
 * <p>A key matches any state of its block with the properties it lists. The value is the state it
 * goes out into: the properties it lists, and every other the two blocks share copied across, so a
 * wall torch keeps its facing. Entries naming a block no mod registers are skipped, so one file can
 * cover mods that may be absent.
 *
 * <p>ZPS's own vanilla torches and lanterns are in {@code zps:vanilla}, which a pack can override.
 * Fire, campfires and candles are built in ({@link Burner}) and come first.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class Extinguishing extends SimpleJsonResourceReloadListener {
    private static final String DIRECTORY = "zps/extinguishing";

    /** One entry: states of a block that match {@code when}, and what they go out into. */
    public record Rule(Map<Property<?>, Comparable<?>> when, Block into, Map<Property<?>, Comparable<?>> set) {
        boolean matches(BlockState state) {
            for (Map.Entry<Property<?>, Comparable<?>> property : when.entrySet()) {
                if (!state.getValue(property.getKey()).equals(property.getValue())) {
                    return false;
                }
            }
            return true;
        }

        BlockState apply(BlockState state) {
            BlockState out = into.withPropertiesOf(state);
            for (Map.Entry<Property<?>, Comparable<?>> property : set.entrySet()) {
                out = with(out, property.getKey(), property.getValue());
            }
            return out;
        }
    }

    private static volatile Map<Block, List<Rule>> rules = Map.of();
    /** Bumped on every load, so flames tracked under the old rules are looked for again. */
    private static volatile int generation;

    private Extinguishing() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Extinguishing());
    }

    /** Whether a datapack names {@code state} as a flame. */
    public static boolean isFlame(BlockState state) {
        List<Rule> forBlock = rules.get(state.getBlock());
        if (forBlock == null) {
            return false;
        }
        for (Rule rule : forBlock) {
            if (rule.matches(state)) {
                return true;
            }
        }
        return false;
    }

    /** What {@code state} goes out into, or null if no datapack names it. */
    public static @Nullable BlockState extinguished(BlockState state) {
        return extinguished(rules, state);
    }

    /** What {@code state} goes out into under {@code rules}, or null if they do not name it. */
    public static @Nullable BlockState extinguished(Map<Block, List<Rule>> rules, BlockState state) {
        List<Rule> forBlock = rules.get(state.getBlock());
        if (forBlock == null) {
            return null;
        }
        for (Rule rule : forBlock) {
            if (rule.matches(state)) {
                return rule.apply(state);
            }
        }
        return null;
    }

    static int generation() {
        return generation;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, List<Rule>> loaded = new HashMap<>();
        files.forEach((file, json) -> {
            if (!json.isJsonObject()) {
                ZPSMod.LOGGER.warn("Extinguishing file {} should be an object of block state to block state", file);
                return;
            }
            parse(file, json.getAsJsonObject(), loaded);
        });
        rules = Map.copyOf(loaded);
        generation++;
    }

    /** Add every entry of {@code json} to {@code into}. Public so a test can build rules without a datapack. */
    public static void parse(ResourceLocation file, JsonObject json, Map<Block, List<Rule>> into) {
        HolderLookup<Block> blocks = BuiltInRegistries.BLOCK.asLookup();
        json.entrySet().forEach(entry -> {
            String from = entry.getKey();
            if (!entry.getValue().isJsonPrimitive()) {
                ZPSMod.LOGGER.warn("Extinguishing {}: {} should map to a block state string", file, from);
                return;
            }
            String to = entry.getValue().getAsString();
            if (!isRegistered(from) || !isRegistered(to)) {
                return;
            }
            try {
                BlockStateParser.BlockResult source = BlockStateParser.parseForBlock(blocks, from, false);
                BlockStateParser.BlockResult target = BlockStateParser.parseForBlock(blocks, to, false);
                Rule rule = new Rule(Map.copyOf(source.properties()), target.blockState().getBlock(), Map.copyOf(target.properties()));
                BlockState out = rule.apply(source.blockState());
                if (out.getBlock() == source.blockState().getBlock() && rule.matches(out)) {
                    // It would go out into a flame of its own, and go on going out every burn.
                    ZPSMod.LOGGER.warn("Extinguishing {}: skipping {} -> {}, which is still {}", file, from, to, from);
                    return;
                }
                into.computeIfAbsent(source.blockState().getBlock(), ignored -> new ArrayList<>())
                        .add(rule);
            } catch (CommandSyntaxException e) {
                ZPSMod.LOGGER.warn("Extinguishing {}: skipping {} -> {}: {}", file, from, to, e.getMessage());
            }
        });
    }

    /** Whether the block a state string names exists; one from an absent mod is quietly skipped. */
    private static boolean isRegistered(String state) {
        int bracket = state.indexOf('[');
        ResourceLocation id = ResourceLocation.tryParse(bracket < 0 ? state : state.substring(0, bracket));
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, Comparable<?> value) {
        return state.setValue(property, (T) value);
    }
}
