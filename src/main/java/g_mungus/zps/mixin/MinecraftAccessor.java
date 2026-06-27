package g_mungus.zps.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes vanilla's right-click cooldown so contraption placement can throttle the
 * held-button auto-repeat exactly like vanilla does (a fresh click via
 * {@code keyUse.consumeClick()} bypasses this delay, so manual re-clicks stay snappy).
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

	@Accessor("rightClickDelay")
	void setRightClickDelay(int value);
}
