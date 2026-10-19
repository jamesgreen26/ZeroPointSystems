package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.gas.ThrusterBlockEntity;
import g_mungus.zps.commands.api.ZPSScriptContext;
import g_mungus.zps.commands.phystick.PhysTickProgrammable;
import g_mungus.zps.compat.Compat;

public class PhysTickCommand {

    /** {@code on_phys_tick}: hands {@code command} to the target's program. */
    public static int onPhysTick(String command, ZPSScriptContext context) {
        if (!(context.level().getBlockEntity(context.pos()) instanceof PhysTickProgrammable target)) {
            return 0;
        }
        if (Compat.gridOf(context.level(), context.pos()) == null) {
            throw new IllegalStateException("on_phys_tick only runs on a ship");
        }
        target.physTickProgram().accept(command, context.commandSource(), context.level(), context.pos());
        return 1;
    }

    /** {@code thrust}: fires the thruster at {@code throttle} for the physics step it runs in, reached through the step. */
    public static int thrust(double throttle, ZPSScriptContext context) {
        if (context.physics() == null) {
            throw new IllegalStateException("thrust only runs inside on_phys_tick");
        }
        if (context.blockEntity() instanceof ThrusterBlockEntity thruster) {
            thruster.setProgramThrottle(throttle);
            return 1;
        }
        return 0;
    }
}
