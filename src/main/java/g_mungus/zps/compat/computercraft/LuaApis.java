package g_mungus.zps.compat.computercraft;

import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.ILuaAPIFactory;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.impl.ApiFactories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computer APIs other mods add to ComputerCraft, such as CC: Sable's {@code sublevel}. A computer
 * gets each one made for it, and the API answers about where the computer is; a script gets one
 * made for a computer standing where the command is aimed.
 */
final class LuaApis {

    private LuaApis() {
    }

    /** One API, by the global name a computer knows it by, and its methods. */
    record Api(ILuaAPIFactory factory, String name, List<Method> methods) {
    }

    /** Every API registered with ComputerCraft that makes itself for a computer at {@code pos}. */
    static List<Api> discover(ServerLevel level, BlockPos pos) {
        List<Api> apis = new ArrayList<>();
        for (ILuaAPIFactory factory : ApiFactories.getAll()) {
            ILuaAPI api;
            try {
                api = factory.create(new ScriptComputer(level, pos));
            } catch (RuntimeException e) {
                // One that needs more of a computer than a script has, such as one of its components.
                continue;
            }
            if (api == null || api.getNames().length == 0) {
                continue;
            }
            List<Method> methods = new ArrayList<>();
            for (Method method : api.getClass().getMethods()) {
                if (method.isAnnotationPresent(LuaFunction.class) && !Modifier.isStatic(method.getModifiers())) {
                    methods.add(method);
                }
            }
            apis.add(new Api(factory, api.getNames()[0], methods));
        }
        return apis;
    }

    /** The Lua names of a method: its own, unless its annotation gives others. */
    static List<String> luaNames(Method method) {
        LuaFunction annotation = method.getAnnotation(LuaFunction.class);
        if (annotation == null) {
            return List.of();
        }
        return annotation.value().length == 0 ? List.of(method.getName()) : List.of(annotation.value());
    }

    /**
     * Calls {@code method} on the API made for a computer at {@code pos}. The arguments fill the
     * method's parameters in order, around the context and computer it may also ask for.
     *
     * @throws LuaException if the API is not made there, or the method fails
     */
    static Object[] call(Api api, Method method, ServerLevel level, BlockPos pos, Object... arguments) throws LuaException {
        ScriptComputer computer = new ScriptComputer(level, pos);
        ILuaAPI instance = api.factory().create(computer);
        if (instance == null) {
            throw new LuaException("The " + api.name() + " API is not available here");
        }
        ScriptComputer.InlineContext context = new ScriptComputer.InlineContext();
        Class<?>[] parameters = method.getParameterTypes();
        Type[] generic = method.getGenericParameterTypes();
        Object[] values = new Object[parameters.length];
        int next = 0;
        for (int i = 0; i < parameters.length; i++) {
            if (ILuaContext.class.isAssignableFrom(parameters[i])) {
                values[i] = context;
            } else if (IComputerAccess.class.isAssignableFrom(parameters[i])) {
                values[i] = computer;
            } else {
                values[i] = parameter(generic[i], next < arguments.length ? arguments[next++] : null);
            }
        }
        Object result;
        try {
            result = method.invoke(instance, values);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof LuaException lua) {
                throw lua;
            }
            throw new LuaException(String.valueOf(e.getCause() == null ? e : e.getCause().getMessage()));
        } catch (IllegalAccessException e) {
            throw new LuaException("Cannot call " + api.name() + "." + method.getName());
        }
        if (result instanceof MethodResult methodResult) {
            return context.finish(methodResult);
        }
        if (result instanceof Object[] array) {
            return array;
        }
        return method.getReturnType() == void.class ? new Object[0] : new Object[]{result};
    }

    /** A script value as the Java parameter it is passed for. */
    private static @Nullable Object parameter(Type parameter, @Nullable Object value) {
        if (parameter instanceof ParameterizedType optional && optional.getRawType() == Optional.class) {
            return Optional.ofNullable(parameter(optional.getActualTypeArguments()[0], value));
        }
        if (!(parameter instanceof Class<?> type)) {
            return value;
        }
        if (value instanceof Number number) {
            if (type == int.class || type == Integer.class) return number.intValue();
            if (type == long.class || type == Long.class) return number.longValue();
            if (type == short.class || type == Short.class) return number.shortValue();
            if (type == byte.class || type == Byte.class) return number.byteValue();
            if (type == double.class || type == Double.class) return number.doubleValue();
            if (type == float.class || type == Float.class) return number.floatValue();
        }
        return value;
    }
}
