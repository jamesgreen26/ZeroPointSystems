package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exposes {@link FallingBlockEntity}'s private positioned constructor so a contraption
 * can detach an unsupported falling block into a real entity at an exact (fractional,
 * rotated) world position, without going through {@code FallingBlockEntity#fall} (which
 * would also mutate a real-world block at the spawn position).
 */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {

	@Invoker("<init>")
	static FallingBlockEntity zps$create(Level level, double x, double y, double z, BlockState state) {
		throw new AssertionError();
	}
}
