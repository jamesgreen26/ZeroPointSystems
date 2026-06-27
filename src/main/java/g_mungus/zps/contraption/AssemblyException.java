package g_mungus.zps.contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Thrown when a contraption cannot be assembled. Mirrors the role of Create's
 * AssemblyException without referencing it.
 */
public class AssemblyException extends Exception {

	private final Component component;

	public AssemblyException(Component component) {
		super(component.getString());
		this.component = component;
	}

	public Component getComponent() {
		return component;
	}

	public static AssemblyException tooLarge() {
		return new AssemblyException(Component.translatable("zps.contraption.too_large"));
	}

	public static AssemblyException unloadedChunk(BlockPos pos) {
		return new AssemblyException(
			Component.translatable("zps.contraption.unloaded_chunk", pos.getX(), pos.getY(), pos.getZ()));
	}

	public static AssemblyException empty() {
		return new AssemblyException(Component.translatable("zps.contraption.empty"));
	}
}
