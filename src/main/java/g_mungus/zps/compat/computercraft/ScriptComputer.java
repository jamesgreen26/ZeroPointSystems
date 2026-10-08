package g_mungus.zps.compat.computercraft;

import dan200.computercraft.api.component.ComputerComponent;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The computer a script calls ComputerCraft from, which there is not: one standing where the
 * command is aimed, so that computer APIs which ask where their computer is answer about that
 * block. It has no file system, no peripherals of its own and no events.
 */
record ScriptComputer(ServerLevel getLevel, BlockPos getPosition) implements IComputerSystem {

    @Override
    public @Nullable String getLabel() {
        return null;
    }

    @Override
    public <T> @Nullable T getComponent(ComputerComponent<T> component) {
        return null;
    }

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

    /**
     * The Lua context a script calls in. Scripts run on the main thread, so main-thread work is done
     * where it is issued; ComputerCraft then waits for task_complete, which {@link #finish} answers.
     */
    static final class InlineContext implements ILuaContext {
        private long lastTask;

        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            task.execute();
            return ++lastTask;
        }

        /** What a call returned, once anything it waits for on the main thread is done. */
        Object[] finish(MethodResult result) throws LuaException {
            for (int i = 0; i < 8 && result.getCallback() != null; i++) {
                result = result.getCallback().resume(new Object[]{"task_complete", lastTask, true});
            }
            if (result.getCallback() != null) {
                throw new LuaException("This waits for something a script cannot give it");
            }
            Object[] values = result.getResult();
            return values == null ? new Object[0] : values;
        }
    }
}
