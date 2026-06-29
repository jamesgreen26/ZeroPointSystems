package g_mungus.zps.client.screens;

import javax.annotation.Nullable;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.client.renderer.contraption.ContraptionRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client-side counterpart to {@link g_mungus.zps.contraption.ContraptionScreenAccess}: resolves the
 * block entity backing a bespoke screen (Robotic Arm, Script Terminal, …). When {@code motorPos} is
 * given, the live block entity comes from the host motor's {@link ContraptionRenderState} cache (so
 * the screen always reads the current instance, which is rebuilt on each contraption re-sync);
 * otherwise it's the plain client-level lookup. Screens call this every frame.
 */
public final class ContraptionScreenAccessClient {

	private ContraptionScreenAccessClient() {
	}

	@Nullable
	public static BlockEntity getBlockEntity(@Nullable BlockPos motorPos, BlockPos localPos) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return null;
		if (motorPos != null) {
			if (level.getBlockEntity(motorPos) instanceof ServoMotorBlockEntity motor) {
				ContraptionRenderState renderState = motor.getRenderState();
				if (renderState != null)
					return renderState.getBlockEntity(localPos);
			}
			return null;
		}
		return level.getBlockEntity(localPos);
	}
}
