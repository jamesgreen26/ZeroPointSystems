package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.zps.compat.GridPhysics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Where a script command runs: the block it is aimed at, and the command source running it. Every
 * ZPS node function gets one, through {@link #of}.
 *
 * @param pos           the block the command is aimed at, the one a Serial Bus faces
 * @param commandSource what relative coordinates and the like are resolved against
 * @param physStep      the physics step a phys tick program is running in, or null outside one. Inside
 *                      one, only words registered as safe there run
 *                      ({@link RegisterScriptCommandsEvent#registerPhysTickSafe}), and they must not
 *                      touch the level.
 */
public record ZPSScriptContext(BlockPos pos, ServerLevel level, CommandSourceStack commandSource,
                               @Nullable PhysStep physStep) {

    /** The grid's physics step, inside a phys tick program; null anywhere else. */
    public @Nullable GridPhysics physics() {
        return physStep == null ? null : physStep.physics();
    }

    /** The target's block entity, read in a way that also works inside a phys tick program. */
    public @Nullable BlockEntity blockEntity() {
        return physStep != null ? physStep.blockEntity() : level.getBlockEntity(pos);
    }

    /** The ZPS context of a run, as the engine hands it to a node. */
    public static ZPSScriptContext of(ScriptContext context) {
        return context.host(ZPSScriptContext.class);
    }
}
