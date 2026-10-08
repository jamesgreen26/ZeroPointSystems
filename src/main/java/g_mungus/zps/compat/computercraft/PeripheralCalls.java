package g_mungus.zps.compat.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.core.methods.MethodSupplier;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.impl.Peripherals;
import dan200.computercraft.shared.computer.core.ServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finding a block's ComputerCraft peripheral and calling its methods with no computer, on the
 * server thread. Uses ComputerCraft's internals as well as its API, so a ComputerCraft update can
 * break it; {@link ComputerCraftCompat} switches itself off if it does.
 */
final class PeripheralCalls {

    private PeripheralCalls() {
    }

    /** A method found on a peripheral, the object it is called on, and where. */
    record Bound(Object target, PeripheralMethod method, ServerLevel level, BlockPos pos) {
    }

    /**
     * The peripheral a block offers from {@code side}, as a computer there would see it: one a mod
     * registers for the block, or else ComputerCraft's generic one.
     *
     * @param state       the block, which need not be in the level
     * @param blockEntity the block's entity, which need not be in the level either
     */
    static @Nullable IPeripheral peripheral(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                            Direction side) {
        IPeripheral found = level.getCapability(PeripheralCapability.get(), pos, state, blockEntity, side);
        return found != null ? found : Peripherals.getGenericPeripheral(level, pos, side, blockEntity);
    }

    /** Every method of a peripheral by its Lua name, as {@code consumer} is handed them. */
    static void forEachMethod(ServerLevel level, IPeripheral peripheral, MethodSupplier.TargetedConsumer<PeripheralMethod> consumer) {
        ServerContext.get(level.getServer()).peripheralMethods().forEachMethod(peripheral, consumer);
    }

    /** The method called {@code name} on the block at {@code pos}, from {@code preferred} first, or null if it has none. */
    static @Nullable Bound find(ServerLevel level, BlockPos pos, @Nullable Direction preferred, String name) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        for (Direction side : sides(preferred)) {
            IPeripheral peripheral = peripheral(level, pos, state, blockEntity, side);
            if (peripheral == null) {
                continue;
            }
            Bound[] found = new Bound[1];
            forEachMethod(level, peripheral, (target, method, implementation, info) -> {
                if (found[0] == null && method.equals(name)) {
                    found[0] = new Bound(target, implementation, level, pos);
                }
            });
            if (found[0] != null) {
                return found[0];
            }
        }
        return null;
    }

    /**
     * Calls a method and returns what it returned. One that would wait for main-thread work gets it
     * done there and then, since this is the main thread; one that waits for anything else fails.
     */
    static Object[] call(Bound bound, Object... arguments) throws LuaException {
        ScriptComputer.InlineContext context = new ScriptComputer.InlineContext();
        return context.finish(bound.method().apply(bound.target(), context,
                new ScriptComputer(bound.level(), bound.pos()), new ObjectArguments(arguments)));
    }

    private static List<Direction> sides(@Nullable Direction preferred) {
        List<Direction> sides = new ArrayList<>();
        if (preferred != null) {
            sides.add(preferred);
        }
        for (Direction side : Direction.values()) {
            if (side != preferred) {
                sides.add(side);
            }
        }
        return sides;
    }
}
