package g_mungus.zps.commands.phystick;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.PhysStep;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridPhysics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The command a {@link PhysTickProgrammable} runs in every physics step, compiled once as a {@link LiveLine}.
 * <p>
 * Words registered as phys tick safe ({@link g_mungus.zps.commands.api.RegisterScriptCommandsEvent#registerPhysTickSafe})
 * run in the step itself. A chain with a word that is not safe is worked out on each server tick. Every executor in
 * it must be safe: only those meant for {@code on_phys_tick} parse in its {@code command(...)}.
 * <p>
 * A program that fails stops. The next {@code on_phys_tick} with the same command starts it again but fails
 * itself with the reason, so whatever sent it shows why, as a Serial Bus does; a program that keeps failing keeps
 * showing it.
 */
public final class PhysTickProgram {
    /** The executor that sends a program, which the words meant for inside one name. */
    public static final String ON_PHYS_TICK = "on_phys_tick";
    /** Game ticks a program keeps running after the last {@code on_phys_tick} that sent it: a second. */
    public static final int STALE_TICKS = 20;

    /** A program as compiled: against which build of the scripts, and the source it was last sent with. */
    private record Compiled(ZPSScripts scripts, LiveLine<CommandSourceStack> line, CommandSourceStack base) {
    }

    /**
     * How many programs a block keeps compiled after they stop being sent. A program that carries its target in its
     * text, as a ship following a beacon does, flips between the same few as the target moves back and forth.
     */
    private static final int RECENT_LIMIT = 4;

    private final Runnable onStopped;
    /** Programs sent before, by their text, as compiled for {@link #recentScripts}. Only touched on the server thread. */
    private final Map<String, LiveLine<CommandSourceStack>> recent = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LiveLine<CommandSourceStack>> eldest) {
            return size() > RECENT_LIMIT;
        }
    };
    private @Nullable ZPSScripts recentScripts;
    private final AtomicReference<@Nullable Compiled> compiled = new AtomicReference<>();
    private final AtomicReference<@Nullable Failure> failure = new AtomicReference<>();
    /** Only touched on the server thread. */
    private long lastSent;

    private record Failure(Throwable cause, String command) {
    }

    /** @param onStopped run whenever a program stops, on whichever thread stopped it */
    public PhysTickProgram(Runnable onStopped) {
        this.onStopped = onStopped;
    }

    public boolean isRunning() {
        return compiled.get() != null;
    }

    /**
     * {@code on_phys_tick}: runs {@code command} from now on, or keeps the one running if it is the same.
     *
     * @param base the source the command was sent with
     * @throws IllegalStateException if this command does not parse or fails as it is first worked out, or it is
     *                               the one that stopped on a failure since it was last sent, which it starts
     *                               again all the same
     */
    public void accept(String command, CommandSourceStack base, ServerLevel level, BlockPos pos) {
        // Read first: a failure on a physics thread from here on is reported by the next one.
        Failure failed = failure.getAndSet(null);
        ZPSScripts scripts = ZPSScripts.get();
        Compiled running = compiled.get();
        if (running == null || running.scripts() != scripts || !running.line().command().equals(command)) {
            try {
                LiveLine<CommandSourceStack> line = recentLine(scripts, command, base, pos);
                line.refresh(ZPSScripts.aimedAt(base, pos));
                compiled.set(new Compiled(scripts, line, base));
            } catch (Exception e) {
                // A command that was meant to replace the running one still stops it.
                if (running != null) {
                    stop(running);
                }
                throw new IllegalStateException(reason(new Failure(e, command)));
            }
        } else {
            // The same program keeps running, as whoever sent it last and in the level it is in now. One that
            // stopped since it was read stays stopped, for the next send to start again.
            compiled.compareAndSet(running, new Compiled(scripts, running.line(), base));
        }
        lastSent = level.getGameTime();
        // One that failed for a command since replaced has nothing to say about this one.
        if (failed != null && failed.command().equals(command)) {
            throw new IllegalStateException("The phys tick program stopped: " + reason(failed));
        }
    }

    /** {@code command} as compiled before for this block, if it was, or compiled now. */
    private LiveLine<CommandSourceStack> recentLine(ZPSScripts scripts, String command, CommandSourceStack base,
                                                    BlockPos pos) throws CommandSyntaxException {
        if (recentScripts != scripts) {
            recent.clear();
            recentScripts = scripts;
        }
        LiveLine<CommandSourceStack> line = recent.get(command);
        if (line == null) {
            line = scripts.compileLive(command, base, pos);
            recent.put(command, line);
        }
        return line;
    }

    /**
     * The server tick: stops a program that has gone stale or whose block has left its grid, which has no more
     * steps for it to run in, and works out what is not safe.
     */
    void tick(ServerLevel level, BlockPos pos) {
        Compiled running = compiled.get();
        if (running == null) {
            return;
        }
        if (level.getGameTime() - lastSent > STALE_TICKS || running.scripts() != ZPSScripts.getIfBuilt()
                || Compat.gridOf(level, pos) == null) {
            stop(running);
            return;
        }
        CommandSourceStack source = ZPSScripts.aimedAt(running.base(), pos);
        try {
            running.line().refresh(source);
        } catch (Exception e) {
            fail(e, running);
        }
    }

    /** One physics step of {@code blockEntity}'s grid; false while no program runs. */
    boolean runPhys(GridPhysics physics, BlockEntity blockEntity) {
        Compiled running = compiled.get();
        if (running == null) {
            return false;
        }
        try {
            CommandSourceStack source = ZPSScripts.aimedAt(running.base(), blockEntity.getBlockPos(),
                    new PhysStep(physics, blockEntity));
            running.line().run(source);
        } catch (Exception e) {
            fail(e, running);
        }
        return true;
    }

    /** Stops {@code running} on {@code e}, unless another program has taken its place since. */
    private void fail(Exception e, Compiled running) {
        if (stop(running)) {
            failure.set(new Failure(e, running.line().command()));
            ZPSMod.LOGGER.debug("Phys tick program '{}' failed", running.line().command(), e);
        }
    }

    /** Stops {@code running} if it is still the program; whether it was. */
    private boolean stop(Compiled running) {
        if (!compiled.compareAndSet(running, null)) {
            return false;
        }
        onStopped.run();
        return true;
    }

    private static String reason(Failure failure) {
        if (failure.cause() instanceof CommandSyntaxException syntax) {
            return syntax.getMessage();
        }
        ZPSScripts scripts = ZPSScripts.getIfBuilt();
        return scripts == null ? String.valueOf(failure.cause().getMessage())
                : scripts.describe(failure.cause(), failure.command(), failure.command(), SourceMap.IDENTITY).reason();
    }
}
