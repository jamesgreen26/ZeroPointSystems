package g_mungus.zps.gametest;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.ServoMotorBlock;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionPath;
import g_mungus.zps.contraption.ContraptionTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for nested contraptions (a Servo Motor mounted on another contraption). Phase A covers the
 * {@link ContraptionTransform} composition algebra that everything else builds on: a nested
 * transform must map points exactly as applying the inner then the parent transform, and remain
 * invertible (so collision/raytrace round-trips hold for arbitrary axis combinations).
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContraptionNestingGameTests {

	private static final String TEMPLATE = "gametest/flat_7x4x7";

	private static boolean near(Vec3 a, Vec3 b) {
		return a.distanceTo(b) < 1e-4;
	}

	/**
	 * {@code inner.andThen(parent)} must equal first applying {@code inner} (into the parent's local
	 * space) and then {@code parent} (into world) — for several points and a mix of rotation axes.
	 */
	@GameTest(template = TEMPLATE)
	public static void composedTransformMatchesManualComposition(GameTestHelper helper) {
		ContraptionTransform parent = ContraptionTransform.leaf(new Vec3(10, 5, 10), 30f, Direction.Axis.Y);
		ContraptionTransform inner = ContraptionTransform.leaf(new Vec3(2, 0, 0), 45f, Direction.Axis.X);
		ContraptionTransform composed = inner.andThen(parent);

		for (Vec3 p : new Vec3[] { new Vec3(0, 0, 0), new Vec3(1.3, 0.7, -0.4), new Vec3(-2, 3, 1.5) }) {
			Vec3 manual = parent.localToWorld(inner.localToWorld(p));
			if (!near(composed.localToWorld(p), manual)) {
				helper.fail("Composed localToWorld " + composed.localToWorld(p) + " != manual " + manual + " for " + p);
				return;
			}
		}
		helper.succeed();
	}

	/** A composed (nested) transform must round-trip: {@code worldToLocal(localToWorld(p)) == p}. */
	@GameTest(template = TEMPLATE)
	public static void composedTransformRoundTrips(GameTestHelper helper) {
		ContraptionTransform composed = ContraptionTransform.leaf(new Vec3(7, 2, -3), 90f, Direction.Axis.Z)
			.andThen(ContraptionTransform.leaf(new Vec3(10, 5, 10), 30f, Direction.Axis.Y));

		Vec3 p = new Vec3(1.25, -0.5, 2.75);
		Vec3 back = composed.worldToLocal(composed.localToWorld(p));
		if (!near(back, p)) {
			helper.fail("Round-trip failed: " + back + " != " + p);
			return;
		}
		// Direction round-trip too (rotation only), since collision/raytrace rotate normals.
		Vec3 d = new Vec3(0.2, 0.8, -0.5);
		if (!near(composed.worldDirToLocal(composed.localDirToWorld(d)), d)) {
			helper.fail("Direction round-trip failed");
			return;
		}
		helper.succeed();
	}

	/**
	 * A pure Y-axis turntable reports no vertical rotation; composing a tilt (X/Z) onto it does. Guards
	 * the collider's "is the platform tilted" decision that replaces {@code ContraptionRotationState}.
	 */
	@GameTest(template = TEMPLATE)
	public static void verticalRotationDetection(GameTestHelper helper) {
		ContraptionTransform flat = ContraptionTransform.leaf(Vec3.ZERO, 47f, Direction.Axis.Y);
		ContraptionTransform tilted = ContraptionTransform.leaf(Vec3.ZERO, 20f, Direction.Axis.X)
			.andThen(ContraptionTransform.leaf(Vec3.ZERO, 47f, Direction.Axis.Y));
		if (flat.hasVerticalRotation()) {
			helper.fail("A Y-axis turntable should report no vertical rotation");
			return;
		}
		if (!tilted.hasVerticalRotation()) {
			helper.fail("A composed X-tilt should report vertical rotation");
			return;
		}
		helper.succeed();
	}

	/**
	 * A platform point on a nested contraption that is <em>not</em> spinning is still carried by the
	 * parent's rotation: differencing the composed current/next-tick poses yields a non-zero velocity.
	 * This is the core of "collisions + carried motion" for nested contraptions.
	 */
	@GameTest(template = TEMPLATE)
	public static void carriedVelocityIncludesParentSpin(GameTestHelper helper) {
		ContraptionTransform parentNow = ContraptionTransform.leaf(new Vec3(10, 5, 10), 0f, Direction.Axis.Y);
		ContraptionTransform parentNext = ContraptionTransform.leaf(new Vec3(10, 5, 10), 2f, Direction.Axis.Y);
		ContraptionTransform nested = ContraptionTransform.leaf(new Vec3(3, 0, 0), 0f, Direction.Axis.Y);

		ContraptionTransform now = nested.andThen(parentNow);
		ContraptionTransform next = nested.andThen(parentNext);
		Vec3 p = new Vec3(1, 0, 0); // a point fixed on the (stationary) nested platform
		Vec3 carried = next.localToWorld(p).subtract(now.localToWorld(p));
		if (carried.length() < 1e-3) {
			helper.fail("A non-spinning nested platform point should still be carried by the parent's spin");
			return;
		}
		helper.succeed();
	}

	/**
	 * The quaternion handed to a detached falling block (a nested contraption's composed orientation)
	 * rotates vectors identically to the transform's local-&gt;world rotation matrix.
	 */
	@GameTest(template = TEMPLATE)
	public static void composedRotationQuaternionMatchesMatrix(GameTestHelper helper) {
		ContraptionTransform t = ContraptionTransform.leaf(Vec3.ZERO, 35f, Direction.Axis.Z)
			.andThen(ContraptionTransform.leaf(Vec3.ZERO, 50f, Direction.Axis.Y));
		Quaternionf q = t.localToWorldRotationQuat();
		Vec3 d = new Vec3(0.3, 0.6, -0.4);
		Vector3f viaQuat = q.transform(new Vector3f((float) d.x, (float) d.y, (float) d.z));
		Vec3 viaMatrix = t.localDirToWorld(d);
		Vec3 quat = new Vec3(viaQuat.x, viaQuat.y, viaQuat.z);
		if (quat.distanceTo(viaMatrix) > 1e-4) {
			helper.fail("Quaternion rotation " + quat + " != matrix rotation " + viaMatrix);
			return;
		}
		helper.succeed();
	}

	private static final BlockPos MOTOR_POS = new BlockPos(2, 2, 2);

	/**
	 * A Servo Motor placed on another contraption is reconstructed + ticked as a live nested motor;
	 * it discovers its host, and a {@link ContraptionPath} round-trips (built by walking up, resolved
	 * by walking down) to the same live instance — the identity mechanism interaction packets rely on.
	 */
	@GameTest(template = TEMPLATE)
	public static void nestedMotorPathResolves(GameTestHelper helper) {
		ServerLevel level = (ServerLevel) helper.getLevel();
		helper.setBlock(MOTOR_POS, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
			.setValue(ServoMotorBlock.FACING, Direction.EAST));
		ServoMotorBlockEntity parent = (ServoMotorBlockEntity) helper.getBlockEntity(MOTOR_POS);
		parent.initContraption();

		// A servo motor sitting on the parent contraption (a nested motor).
		Contraption pc = parent.getContraption();
		BlockPos nestedCell = new BlockPos(1, 0, 0);
		pc.putBlock(nestedCell, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
			.setValue(ServoMotorBlock.FACING, Direction.UP), null, null);

		// Ticking the parent reconstructs the nested motor as a live block entity and ticks it, so it
		// seeds its own contraption (head).
		parent.serverTick();

		ServoMotorBlockEntity nested = parent.getNestedMotor(nestedCell);
		if (nested == null) {
			helper.fail("Nested motor should be reconstructed as a live block entity");
			return;
		}
		if (nested.hostMotor() != parent) {
			helper.fail("Nested motor's host should be the parent motor");
			return;
		}
		if (nested.getContraption() == null) {
			helper.fail("Nested motor should have initialised its own contraption");
			return;
		}

		ContraptionPath path = ContraptionPath.of(nested);
		if (!path.rootMotorPos().equals(parent.getBlockPos()) || path.nestedCells().size() != 1
			|| !path.nestedCells().get(0).equals(nestedCell)) {
			helper.fail("Path should be (parentPos, [nestedCell]) but was " + path);
			return;
		}
		if (path.resolve(level) != nested) {
			helper.fail("Resolving the path should return the same live nested motor instance");
			return;
		}
		helper.succeed();
	}
}
