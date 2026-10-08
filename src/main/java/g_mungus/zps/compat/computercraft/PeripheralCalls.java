package g_mungus.zps.compat.computercraft;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.api.peripheral.WorkMonitor;
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
import java.util.Map;

/**
 * Finding a block's ComputerCraft peripheral and calling its methods with no computer, on the
 * server thread. Uses ComputerCraft's internals as well as its API, so a ComputerCraft update can
 * break it; {@link ComputerCraftCompat} switches itself off if it does.
 */
final class PeripheralCalls {

    private PeripheralCalls() {
    }

    /** A method found on a peripheral, and the object it is called on. */
    record Bound(Object target, PeripheralMethod method) {
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
                    found[0] = new Bound(target, implementation);
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
        InlineContext context = new InlineContext();
        MethodResult result = bound.method().apply(bound.target(), context, NoComputer.INSTANCE, new ObjectArguments(arguments));
        for (int i = 0; i < 8 && result.getCallback() != null; i++) {
            result = result.getCallback().resume(new Object[]{"task_complete", context.lastTask, true});
        }
        if (result.getCallback() != null) {
            throw new LuaException("This waits for something a script cannot give it");
        }
        Object[] values = result.getResult();
        return values == null ? new Object[0] : values;
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

    /** Runs main-thread tasks where they are issued; ComputerCraft then waits for task_complete. */
    private static final class InlineContext implements ILuaContext {
        private long lastTask;

        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            task.execute();
            return ++lastTask;
        }
    }

    /** The computer a method is called from, which there is not. */
    private enum NoComputer implements IComputerAccess {
        INSTANCE;

        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            throw new UnsupportedOperationException("Scripts have no file system");
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            throw new UnsupportedOperationException("Scripts have no file system");
        }

        @Override
        public void unmount(@Nullable String location) {
        }

        @Override
        public int getID() {
            return -1;
        }

        @Override
        public void queueEvent(String event, @Nullable Object... arguments) {
        }

        @Override
        public String getAttachmentName() {
            return "zps";
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public @Nullable IPeripheral getAvailablePeripheral(String name) {
            return null;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            throw new UnsupportedOperationException("Scripts have no main thread monitor");
        }
    }
}
