package g_mungus.zps.commands.phystick;

import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.compat.GridPhysicsActor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that takes {@code on_phys_tick command(...)}: it keeps the command and runs it in every physics
 * step of the grid it is on, until the command is replaced or not sent again for
 * {@link PhysTickProgram#STALE_TICKS} game ticks.
 * <p>
 * Call {@link #tickPhysTickProgram} from the server tick and {@link #runPhysTickProgram} at the start of
 * {@link #onGridPhysicsTick}, before reading anything the program sets. Give the block its own executors to act on,
 * registered with {@link g_mungus.zps.commands.api.RegisterScriptCommandsEvent#registerPhysTickExecutor}: no other
 * executor parses inside {@code on_phys_tick}.
 */
public interface PhysTickProgrammable extends GridPhysicsActor {

    /** This block entity's program, one for its lifetime. Programs are not saved. */
    PhysTickProgram physTickProgram();

    /** Puts back whatever the program set, once it has stopped. May be called on a physics thread. */
    default void onPhysTickProgramStopped() {
    }

    /** The server tick's part: the values worked out ahead, and staleness. */
    default void tickPhysTickProgram() {
        BlockEntity blockEntity = (BlockEntity) this;
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            physTickProgram().tick(level, blockEntity.getBlockPos());
        }
    }

    /** Runs the program in this physics step; false, doing nothing, while there is none. */
    default boolean runPhysTickProgram(GridPhysics physics) {
        return physTickProgram().runPhys(physics, (BlockEntity) this);
    }
}
