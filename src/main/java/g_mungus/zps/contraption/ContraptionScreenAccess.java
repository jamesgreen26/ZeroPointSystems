package g_mungus.zps.contraption;

import javax.annotation.Nullable;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side resolution and reach checks for bespoke screens (Robotic Arm, Script Terminal, …) whose
 * blocks can ride a contraption. Such screens are not {@code MenuProvider} menus, so they don't go
 * through the {@code MenuType.create} path that {@link ContraptionMenuContext} handles; instead each
 * carries an optional host motor position alongside the block's contraption-LOCAL position, and
 * resolves the live block entity through that motor here. When {@code motorPos} is {@code null} the
 * block is an ordinary world block and behaviour is unchanged.
 */
public final class ContraptionScreenAccess {

	/** Vanilla container reach: 8 blocks from the player to the block centre, squared. */
	private static final double REACH_SQR = 64.0;

	private ContraptionScreenAccess() {
	}

	/**
	 * The live server block entity at {@code localPos}: the host motor's contraption block entity when
	 * {@code motorPos} is given (and the motor exists), otherwise the plain world lookup.
	 */
	@Nullable
	public static BlockEntity resolve(ServerLevel level, @Nullable BlockPos motorPos, BlockPos localPos) {
		if (motorPos != null) {
			if (level.getBlockEntity(motorPos) instanceof ServoMotorBlockEntity motor)
				return motor.getContraptionBlockEntity(localPos);
			return null;
		}
		return level.getBlockEntity(localPos);
	}

	/**
	 * Whether {@code player} is within container reach of the block. For a contraption block the test
	 * is against the block's rotated WORLD position (via the motor's transform); otherwise it mirrors
	 * the prior {@link Compat#toWorldPos} check so VS/Sable sublevels keep working.
	 */
	public static boolean inReach(ServerPlayer player, @Nullable BlockPos motorPos, BlockPos localPos) {
		ServerLevel level = player.serverLevel();
		Vec3 worldCenter;
		if (motorPos != null && level.getBlockEntity(motorPos) instanceof ServoMotorBlockEntity motor) {
			worldCenter = ContraptionTransform.ofCurrent(motor).localBlockCenterToWorld(localPos);
		} else {
			worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(localPos));
		}
		return worldCenter.distanceToSqr(player.position()) <= REACH_SQR;
	}
}
