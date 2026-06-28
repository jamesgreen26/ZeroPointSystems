package g_mungus.zps.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes vanilla's held-mining cooldown so contraption block breaking can share the
 * exact same throttle as breaking a regular block (5-tick gap between consecutive held
 * breaks; a fresh click bypasses it).
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

	@Accessor("destroyDelay")
	int getDestroyDelay();

	@Accessor("destroyDelay")
	void setDestroyDelay(int value);
}
