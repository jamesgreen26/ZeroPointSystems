package g_mungus.zps.commands.api;

import g_mungus.zps.compat.GridPhysics;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The physics step a phys tick program is running in, and the block entity running it: the program's target.
 * The level cannot be asked for block entities from a physics thread, so words reach their target through this.
 */
public record PhysStep(GridPhysics physics, BlockEntity blockEntity) {
}
