package g_mungus.zps.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.GenericMethod;
import dan200.computercraft.impl.GenericSources;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.SimpleExecutor;
import g_mungus.munguscript.language.node.SimpleGetter;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Script commands from ComputerCraft peripherals: whatever a computer could call on a block, a
 * script can too, where it fits the script language. That is every block ComputerCraft knows,
 * from any mod, without support for each: its own, other mods' integrations, and the inventory,
 * fluid and energy methods it gives any block with those capabilities.
 *
 * <ul>
 *   <li>A method that takes nothing, returns a number, string or boolean, and is named as a question
 *       ({@code get}, {@code is}, {@code has}, {@code can}) is a getter, named in snake
 *       case without a leading {@code get}: {@code getEnergyCapacity} is {@code energy_capacity}.
 *   <li>A method that takes one number, string or boolean and returns nothing or a boolean is an
 *       executor, named in snake case, unless its name says it is a question.
 * </ul>
 * Everything else, such as methods returning tables or taking several arguments, is left out. A
 * getter whose name is taken is left out too.
 *
 * <p>Computer APIs other mods add, such as CC: Sable's {@code sublevel}, are taken by the same
 * rules and named after the API ({@code sublevel_name}). They answer about where the command is
 * aimed, as they would about where a computer stands.
 *
 * <p>Finding the methods needs a level, so this registers nothing when scripts are first built,
 * and its commands appear when they are built again once the server has started.
 */
public final class ComputerCraftCompat {
    private static final List<String> QUESTIONS = List.of("get", "is", "has", "list", "can");

    private ComputerCraftCompat() {
    }

    /**
     * @param apiApplicability which targets a computer API is for, by its name, where that is known;
     *                         an API with none is for every block
     */
    public static void registerScriptCommands(RegisterScriptCommandsEvent event,
                                              Map<String, ? extends TargetApplicability> apiApplicability) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.overworld();
        if (level == null) {
            return;
        }
        try {
            register(event, discover(level));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            ZPSMod.LOGGER.error("Could not read ComputerCraft's peripherals; scripts get no commands for them", e);
        }
        try {
            registerApis(event, LuaApis.discover(level, BlockPos.ZERO), apiApplicability);
        } catch (LinkageError | RuntimeException e) {
            ZPSMod.LOGGER.error("Could not read ComputerCraft's computer APIs; scripts get no commands for them", e);
        }
    }

    /** One method as a script would call it, and the blocks that have it. */
    private record Found(String luaName, @Nullable ScriptType<?> returns, @Nullable ScriptType<?> argument,
                         Set<ResourceLocation> blocks) {
    }

    /** Every method of every block's peripheral that fits a getter or an executor, by Lua name and shape. */
    private static Map<String, Found> discover(ServerLevel level) throws ReflectiveOperationException {
        Signatures signatures = new Signatures();
        Map<String, Found> found = new LinkedHashMap<>();
        BlockPos probe = BlockPos.ZERO;
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (!(entry.getValue() instanceof EntityBlock entityBlock)) {
                continue;
            }
            ResourceLocation block = entry.getKey().location();
            try {
                BlockState state = entry.getValue().defaultBlockState();
                BlockEntity blockEntity = entityBlock.newBlockEntity(probe, state);
                if (blockEntity == null) {
                    continue;
                }
                blockEntity.setLevel(level);
                try {
                    probe(level, probe, state, blockEntity, block, signatures, found);
                } finally {
                    // Never placed, so it has to be ended as a removed one would be: mods that queue
                    // work for a block entity, such as ComputerCraft's monitors, skip removed ones.
                    blockEntity.setRemoved();
                }
            } catch (Throwable t) {
                // Some block entities will not be made outside a world; ComputerCraft sees those only in one.
            }
        }
        return found;
    }

    private static void probe(ServerLevel level, BlockPos probe, BlockState state, BlockEntity blockEntity,
                              ResourceLocation block, Signatures signatures, Map<String, Found> found) {
                for (Direction side : Direction.values()) {
                    IPeripheral peripheral = PeripheralCalls.peripheral(level, probe, state, blockEntity, side);
                    if (peripheral == null) {
                        continue;
                    }
                    PeripheralCalls.forEachMethod(level, peripheral, (target, name, method, info) -> {
                        Method java = signatures.find(target, name);
                        Shape shape = java == null ? null : Shape.of(java, signatures.isGeneric(java));
                        if (shape == null) {
                            return;
                        }
                        String key = name + "/" + shape;
                        found.computeIfAbsent(key, ignored -> new Found(name, shape.returns(), shape.argument(), new HashSet<>()))
                                .blocks().add(block);
                    });
                }
    }

    private static void register(RegisterScriptCommandsEvent event, Map<String, Found> found) {
        Map<String, Found> getters = new LinkedHashMap<>();
        int executors = 0;
        for (Found method : found.values()) {
            if (method.argument() == null && method.returns() != null && isQuery(method.luaName())) {
                String name = getterName(method.luaName());
                Found existing = getters.get(name);
                if (existing == null) {
                    getters.put(name, method);
                } else if (existing.returns() == method.returns() && existing.luaName().equals(method.luaName())) {
                    existing.blocks().addAll(method.blocks());
                } else {
                    ZPSMod.LOGGER.warn("ComputerCraft methods {} and {} would both be the getter {}; only the first is",
                            existing.luaName(), method.luaName(), name);
                }
            } else if (method.argument() != null && !isQuestion(method.luaName())
                    && (method.returns() == null || method.returns() == BuiltInTypes.BOOLEAN)) {
                event.register(executor(method));
                executors++;
            }
        }
        int registered = 0;
        for (Map.Entry<String, Found> entry : getters.entrySet()) {
            String name = entry.getKey();
            Found method = entry.getValue();
            if (event.hasGetter(name)) {
                ZPSMod.LOGGER.warn("ComputerCraft method {} would be the getter {}, which already exists; it gets none",
                        method.luaName(), name);
            } else {
                event.register(getter(name, method));
                registered++;
            }
        }
        ZPSMod.LOGGER.info("Scripts can use {} ComputerCraft getters and {} executors", registered, executors);
    }

    /**
     * Computer APIs, such as CC: Sable's {@code sublevel}: their methods by the same rules as a
     * peripheral's, named after the API, so {@code sublevel.getName()} is {@code sublevel_name}.
     * They answer about where the command is aimed, as they would about where a computer stands,
     * so they are for every block unless {@code apiApplicability} says otherwise.
     */
    private static void registerApis(RegisterScriptCommandsEvent event, List<LuaApis.Api> apis,
                                     Map<String, ? extends TargetApplicability> apiApplicability) {
        int getters = 0;
        int executors = 0;
        for (LuaApis.Api api : apis) {
            @Nullable TargetApplicability applicability = apiApplicability.get(api.name());
            for (Method method : api.methods()) {
                Shape shape = Shape.of(method, false);
                if (shape == null) {
                    continue;
                }
                for (String luaName : LuaApis.luaNames(method)) {
                    String prefix = snakeCase(api.name()) + "_";
                    if (shape.argument() == null && shape.returns() != null && isQuery(luaName)) {
                        String name = prefix + getterName(luaName);
                        if (event.hasGetter(name)) {
                            ZPSMod.LOGGER.warn("ComputerCraft's {}.{} would be the getter {}, which already exists; it gets none",
                                    api.name(), luaName, name);
                            continue;
                        }
                        event.register(apiGetter(name, api, method, shape.returns()).withApplicability(applicability));
                        getters++;
                    } else if (shape.argument() != null && !isQuestion(luaName)
                            && (shape.returns() == null || shape.returns() == BuiltInTypes.BOOLEAN)) {
                        event.register(apiExecutor(prefix + snakeCase(luaName), api, method, shape.argument())
                                .withApplicability(applicability));
                        executors++;
                    }
                }
            }
        }
        ZPSMod.LOGGER.info("Scripts can use {} getters and {} executors from ComputerCraft computer APIs", getters, executors);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SimpleGetter<?> apiGetter(String name, LuaApis.Api api, Method method, ScriptType type) {
        return ZPSNodes.getter(name, type, context -> {
            Object[] result = callApi(api, method, context);
            return result.length == 0 || result[0] == null ? emptyValue(type) : toScript(type, result[0]);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SimpleExecutor<?, ?> apiExecutor(String name, LuaApis.Api api, Method method, ScriptType type) {
        return ZPSNodes.executor(name, type, (argument, context) -> {
            Object[] result = callApi(api, method, context, argument);
            return result.length > 0 && Boolean.FALSE.equals(result[0]) ? 0 : 1;
        });
    }

    private static Object[] callApi(LuaApis.Api api, Method method, ZPSScriptContext context, Object... arguments) {
        try {
            return LuaApis.call(api, method, context.level(), context.pos(), arguments);
        } catch (LuaException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScriptNode getter(String name, Found method) {
        ScriptType type = method.returns();
        return ZPSNodes.getter(name, type, context -> {
            Object value = callOrNull(context, method.luaName());
            return value == null ? emptyValue(type) : toScript(type, value);
        }).withApplicability(BlockApplicability.ofBlocks(method.blocks()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScriptNode executor(Found method) {
        ScriptType type = method.argument();
        return ZPSNodes.executor(snakeCase(method.luaName()), type, (argument, context) -> {
            PeripheralCalls.Bound bound = PeripheralCalls.find(context.level(), context.pos(), side(context), method.luaName());
            if (bound == null) {
                return 0;
            }
            Object[] result = callOrThrow(bound, argument);
            return result.length > 0 && Boolean.FALSE.equals(result[0]) ? 0 : 1;
        }).withApplicability(BlockApplicability.ofBlocks(method.blocks()));
    }

    /** What the method returns at the target, or null where it has no such method. */
    private static @Nullable Object callOrNull(ZPSScriptContext context, String luaName) {
        PeripheralCalls.Bound bound = PeripheralCalls.find(context.level(), context.pos(), side(context), luaName);
        if (bound == null) {
            return null;
        }
        Object[] result = callOrThrow(bound);
        return result.length == 0 ? null : result[0];
    }

    private static Object[] callOrThrow(PeripheralCalls.Bound bound, Object... arguments) {
        try {
            return PeripheralCalls.call(bound, arguments);
        } catch (LuaException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** The face of the target toward whoever runs the command, as a computer there would ask from. */
    private static @Nullable Direction side(ZPSScriptContext context) {
        Vec3 from = context.commandSource().getPosition().subtract(Vec3.atCenterOf(context.pos()));
        return from.lengthSqr() < 1.0E-4 ? null : Direction.getNearest(from.x, from.y, from.z);
    }

    private static Object toScript(ScriptType<?> type, Object value) {
        if (type == BuiltInTypes.INT && value instanceof Number number) {
            return number.intValue();
        }
        if (type == BuiltInTypes.DOUBLE && value instanceof Number number) {
            return number.doubleValue();
        }
        if (type == BuiltInTypes.BOOLEAN && value instanceof Boolean bool) {
            return bool;
        }
        if (type == BuiltInTypes.STRING) {
            return String.valueOf(value);
        }
        throw new IllegalStateException("ComputerCraft gave " + value + ", which is not a " + type.key().path());
    }

    private static Object emptyValue(ScriptType<?> type) {
        if (type == BuiltInTypes.INT) return 0;
        if (type == BuiltInTypes.DOUBLE) return 0.0;
        if (type == BuiltInTypes.BOOLEAN) return false;
        return "";
    }

    static String getterName(String luaName) {
        boolean getPrefix = luaName.length() > 3 && luaName.startsWith("get") && Character.isUpperCase(luaName.charAt(3));
        return snakeCase(getPrefix ? luaName.substring(3) : luaName);
    }

    static String snakeCase(String luaName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < luaName.length(); i++) {
            char c = luaName.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean afterLower = i > 0 && !Character.isUpperCase(luaName.charAt(i - 1));
                boolean beforeLower = i + 1 < luaName.length() && Character.isLowerCase(luaName.charAt(i + 1))
                        && i > 0 && Character.isUpperCase(luaName.charAt(i - 1));
                if (out.length() > 0 && (afterLower || beforeLower)) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a method that takes nothing only reads. Reading a getter must not do anything, and a
     * method like {@code extend} or {@code toggle} returns a value too, so only names that ask are
     * taken. That leaves out {@code size} too, which every inventory has and says little.
     */
    private static boolean isQuery(String luaName) {
        return isQuestion(luaName);
    }

    private static boolean isQuestion(String luaName) {
        for (String question : QUESTIONS) {
            if (luaName.startsWith(question) && (luaName.length() == question.length()
                    || Character.isUpperCase(luaName.charAt(question.length())))) {
                return true;
            }
        }
        return false;
    }

    /** What a method takes and gives, as script types: at most one argument, and a value or nothing. */
    private record Shape(@Nullable ScriptType<?> argument, @Nullable ScriptType<?> returns) {

        /** @param generic whether the method's first parameter is the object it is called on */
        static @Nullable Shape of(Method method, boolean generic) {
            List<Type> arguments = new ArrayList<>();
            Type[] parameters = method.getGenericParameterTypes();
            for (int i = generic ? 1 : 0; i < parameters.length; i++) {
                Class<?> raw = raw(parameters[i]);
                if (ILuaContext.class.isAssignableFrom(raw) || IComputerAccess.class.isAssignableFrom(raw)) {
                    continue;
                }
                if (raw == IArguments.class) {
                    return null;
                }
                arguments.add(parameters[i]);
            }
            if (arguments.size() > 1) {
                return null;
            }
            ScriptType<?> argument = null;
            if (arguments.size() == 1) {
                argument = scalar(arguments.get(0));
                if (argument == null) {
                    return null;
                }
            }
            ScriptType<?> returns = scalar(method.getGenericReturnType());
            if (argument == null && returns == null) {
                return null;
            }
            return new Shape(argument, returns);
        }

        @Override
        public String toString() {
            return (argument == null ? "-" : argument.key().path()) + ">" + (returns == null ? "-" : returns.key().path());
        }

        private static @Nullable ScriptType<?> scalar(Type type) {
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class) {
                return scalar(parameterized.getActualTypeArguments()[0]);
            }
            Class<?> raw = raw(type);
            if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                    || raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class) {
                return BuiltInTypes.INT;
            }
            if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
                return BuiltInTypes.DOUBLE;
            }
            if (raw == boolean.class || raw == Boolean.class) {
                return BuiltInTypes.BOOLEAN;
            }
            if (raw == String.class) {
                return BuiltInTypes.STRING;
            }
            return null;
        }

        private static Class<?> raw(Type type) {
            if (type instanceof Class<?> c) return c;
            if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
            return Object.class;
        }
    }

    /**
     * The Java method behind a Lua method name: one on the object itself, or a generic one, which
     * ComputerCraft keeps without exposing its method, so that is read reflectively.
     */
    private static final class Signatures {
        private final List<GenericEntry> generic = new ArrayList<>();
        private final Set<Method> genericMethods = new HashSet<>();

        private record GenericEntry(Class<?> target, Method method) {
        }

        Signatures() throws ReflectiveOperationException {
            Field methodField = GenericMethod.class.getDeclaredField("method");
            Field targetField = GenericMethod.class.getDeclaredField("target");
            methodField.setAccessible(true);
            targetField.setAccessible(true);
            for (GenericMethod method : GenericSources.getAllMethods()) {
                Method java = (Method) methodField.get(method);
                generic.add(new GenericEntry((Class<?>) targetField.get(method), java));
                genericMethods.add(java);
            }
        }

        boolean isGeneric(Method method) {
            return genericMethods.contains(method);
        }

        @Nullable Method find(Object target, String luaName) {
            for (Method method : target.getClass().getMethods()) {
                if (luaNames(method).contains(luaName)) {
                    return method;
                }
            }
            for (GenericEntry entry : generic) {
                if (entry.target().isInstance(target) && luaNames(entry.method()).contains(luaName)) {
                    return entry.method();
                }
            }
            return null;
        }

        private static List<String> luaNames(Method method) {
            LuaFunction annotation = method.getAnnotation(LuaFunction.class);
            if (annotation == null) {
                return List.of();
            }
            return annotation.value().length == 0 ? List.of(method.getName()) : List.of(annotation.value());
        }
    }
}
