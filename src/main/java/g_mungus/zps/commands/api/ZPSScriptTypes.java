package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import g_mungus.zps.ZPSMod;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The script types ZPS adds to the built-in {@code int}, {@code double}, {@code string} and
 * {@code boolean} ({@link BuiltInTypes}). Each prints the way its {@code as_string} reads, since
 * that is also the text a value becomes wherever a string is wanted.
 */
public final class ZPSScriptTypes {

    public static final ScriptType<BlockPos> BLOCK_POS = ScriptType
            .writable(key("block_pos"), BlockPos.class, Coordinates.class,
                    (coordinates, context) -> coordinates.getBlockPos(ZPSScriptContext.of(context).commandSource()))
            .argument(BlockPosArgument.blockPos())
            .print(pos -> pos.getX() + " " + pos.getY() + " " + pos.getZ())
            .parse(ZPSScriptTypes::parseBlockPos)
            .build();

    public static final ScriptType<Vec3> VEC_POS = vector("vec_pos");
    public static final ScriptType<Vec3> VEC_DIR = vector("vec_dir");
    public static final ScriptType<Vec3> VEC_BOX = vector("vec_box");

    /** A dimension's id, such as {@code minecraft:overworld}. */
    public static final ScriptType<String> DIMENSION = ScriptType
            .writable(key("dimension"), String.class, ResourceLocation.class, (id, context) -> id.toString())
            .argument(DimensionArgument.dimension())
            .print(id -> id)
            .parse(text -> ResourceLocation.parse(text.strip()).toString())
            .build();

    public static final ScriptType<ItemStack> ITEM = ScriptType.opaque(key("item"), ItemStack.class);

    public static final ScriptType<BlockState> BLOCK_STATE = ScriptType.opaque(key("block_state"), BlockState.class);

    private static final List<ScriptType<?>> CORE = List.of(BLOCK_POS, VEC_POS, VEC_DIR, VEC_BOX, DIMENSION, ITEM, BLOCK_STATE);

    private ZPSScriptTypes() {
    }

    /** A key in ZPS's namespace, which scripts write by path alone. */
    public static TypeKey key(String path) {
        return new TypeKey(ZPSMod.MOD_ID, path);
    }

    /**
     * Every type ZPS and its compat register, built-ins aside, and every kind of applicability.
     * Server and client both build from this, so a tree sent from one reads on the other.
     */
    public static Registered collect() {
        List<ScriptType<?>> types = new ArrayList<>(CORE);
        Map<ResourceLocation, TargetApplicability.Type<?>> applicabilities = new LinkedHashMap<>();
        applicabilities.put(BlockApplicability.TYPE.id(), BlockApplicability.TYPE);
        NeoForge.EVENT_BUS.post(new RegisterScriptTypesEvent(types::add,
                type -> applicabilities.put(type.id(), type)));
        return new Registered(List.copyOf(types), Map.copyOf(applicabilities));
    }

    /** What {@link #collect} found: script types, and kinds of applicability by id. */
    public record Registered(List<ScriptType<?>> types, Map<ResourceLocation, TargetApplicability.Type<?>> applicabilities) {
    }

    public static String formatDouble(double value) {
        return BuiltInTypes.formatDouble(value);
    }

    public static String formatVec3(Vec3 vec3) {
        return formatDouble(vec3.x) + " " + formatDouble(vec3.y) + " " + formatDouble(vec3.z);
    }

    private static ScriptType<Vec3> vector(String path) {
        return ScriptType
                .writable(key(path), Vec3.class, Coordinates.class,
                        (coordinates, context) -> coordinates.getPosition(ZPSScriptContext.of(context).commandSource()))
                .argument(Vec3Argument.vec3())
                .print(ZPSScriptTypes::formatVec3)
                .parse(ZPSScriptTypes::parseVec3)
                .build();
    }

    private static BlockPos parseBlockPos(String text) {
        String[] parts = parts(text);
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static Vec3 parseVec3(String text) {
        String[] parts = parts(text);
        return new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    }

    private static String[] parts(String text) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected three numbers, got \"" + text + "\"");
        }
        return parts;
    }
}
