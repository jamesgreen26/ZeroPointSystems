package g_mungus.zps.contraption;

import javax.annotation.Nullable;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;

/**
 * Implemented by the wrapped levels that host a contraption's blocks — the server
 * {@link ContraptionSimServerLevel} and the client {@code ContraptionRenderLevel}. Lets a Servo
 * Motor reconstructed <em>inside</em> another contraption discover the motor that hosts it, so its
 * {@link ContraptionTransform} can compose through the parent's pose (nested contraptions).
 *
 * <p>Dist-safe by design: {@link ServoMotorBlockEntity} dispatches on this interface rather than on
 * the client-only render level type, keeping the block entity free of client references.
 */
public interface ContraptionHostLevel {

	/** The Servo Motor whose contraption this level hosts, or {@code null} if it cannot be resolved. */
	@Nullable
	ServoMotorBlockEntity getContraptionHostMotor();
}
