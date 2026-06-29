package g_mungus.zps.contraption;

import javax.annotation.Nullable;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.client.renderer.contraption.ContraptionRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Lets <i>any</i> menu-backed block open its GUI while riding a contraption, with no per-block or
 * per-menu patching — purely client-side.
 *
 * <p><b>The problem.</b> A block used on a contraption runs its vanilla {@code useWithoutItem}
 * against the {@link ContraptionSimServerLevel}, so the {@link BlockPos} it hands to
 * {@code openMenu} is the contraption-LOCAL position. The standard menu-open round-trip writes that
 * local pos to the client, whose menu factory then resolves the backing block entity via the
 * near-universal {@code level.getBlockEntity(pos)} pattern — but against the REAL client level,
 * where nothing exists at the local pos — and throws.
 *
 * <p><b>The fix.</b> When the client sends a contraption "use" packet it records the targeted
 * {motor, localPos} here ({@link #beginUse}). While a menu is being constructed from server data —
 * bracketed by a mixin around {@code MenuType.create} via {@link #beginConstruction}/
 * {@link #endConstruction} — a mixin on {@code Level#getBlockEntity} consults {@link #resolve}: a
 * lookup at the recorded local pos is redirected to the live client-side block entity taken from the
 * host motor's {@link ContraptionRenderState}. The menu only needs <i>a</i> block entity to back its
 * slots/{@code ContainerData}; the actual contents arrive through normal menu sync, exactly as for a
 * world block.
 *
 * <p>All state is touched only on the client main thread (use-packet send and menu construction both
 * run there); the {@code Level#getBlockEntity} mixin additionally guards on {@code isClientSide} so
 * the integrated server's lookups are never redirected.
 */
public final class ContraptionMenuContext {

	@Nullable
	private static ContraptionPath path;
	@Nullable
	private static BlockPos localPos;
	private static boolean constructing;
	/** Guards the {@link #resolve} motor lookup, which re-enters {@code Level#getBlockEntity}. */
	private static boolean resolving;

	private ContraptionMenuContext() {
	}

	/** Record the contraption block a use was just sent for, so the menu it opens can be resolved. */
	public static void beginUse(ContraptionPath path, BlockPos localPos) {
		ContraptionMenuContext.path = path;
		ContraptionMenuContext.localPos = localPos.immutable();
	}

	/** A menu is being constructed from server data; scopes {@link #resolve} to exactly that window. */
	public static void beginConstruction() {
		constructing = true;
	}

	/** End of menu construction (paired with {@link #beginConstruction}); consumes the pending use. */
	public static void endConstruction() {
		constructing = false;
		path = null;
		localPos = null;
	}

	/**
	 * While a menu is being constructed, redirect a block-entity lookup at the recorded contraption-
	 * local position to the host motor's live client block entity. Returns {@code null} (no redirect)
	 * otherwise — including for the motor lookup below, whose position never equals the local pos.
	 */
	@Nullable
	public static BlockEntity resolve(Level level, BlockPos pos) {
		if (resolving || !constructing || localPos == null || path == null || !pos.equals(localPos))
			return null;
		resolving = true;
		try {
			ServoMotorBlockEntity motor = path.resolve(level);
			if (motor != null) {
				ContraptionRenderState renderState = motor.getRenderState();
				if (renderState != null)
					return renderState.getBlockEntity(pos);
			}
			return null;
		} finally {
			resolving = false;
		}
	}
}
