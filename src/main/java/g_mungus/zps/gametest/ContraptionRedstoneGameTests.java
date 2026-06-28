package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.ServoMotorBlock;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionSimServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies that a contraption simulates ordinary block interactions and non-block-entity redstone:
 * the server-side {@link ContraptionSimServerLevel} is a real {@code ServerLevel}, so buttons,
 * levers, doors and redstone (dust/repeaters/observers/torches/lamps) behave as in a normal world.
 *
 * <p>Interactions are driven directly against the simulation level with a {@code FakePlayer} (the
 * same trick {@code CableNetworkGameTests} uses), avoiding the mock-{@code ServerPlayer} S2C crash.
 * Scheduled redstone ticks are advanced by waiting real game ticks (so the world game-time moves)
 * and then pumping the motor's tick loop via {@link ServoMotorBlockEntity#serverTick()}.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContraptionRedstoneGameTests {

	private static final String TEMPLATE = "gametest/flat_7x4x7";
	private static final BlockPos MOTOR_POS = new BlockPos(2, 2, 2);

	// region helpers

	private static ServoMotorBlockEntity setupMotor(GameTestHelper helper) {
		helper.setBlock(MOTOR_POS, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
			.setValue(ServoMotorBlock.FACING, Direction.EAST));
		ServoMotorBlockEntity motor = (ServoMotorBlockEntity) helper.getBlockEntity(MOTOR_POS);
		motor.initContraption();
		return motor;
	}

	private static ContraptionSimServerLevel sim(GameTestHelper helper, Contraption c) {
		return new ContraptionSimServerLevel((ServerLevel) helper.getLevel(), c, null, null);
	}

	/** Insert a block into the structure without firing updates (builds the inert layout). */
	private static void put(Contraption c, BlockPos local, BlockState state) {
		c.putBlock(local, state, null, null);
	}

	/** Place a block through the sim level so neighbour/redstone updates fire (mirrors placement). */
	private static void place(GameTestHelper helper, Contraption c, BlockPos local, BlockState state) {
		sim(helper, c).setBlock(local, state, Block.UPDATE_ALL);
	}

	/** Right-click (use) a block in the structure with a fake player, like a real interaction. */
	private static void use(GameTestHelper helper, Contraption c, BlockPos local) {
		ServerLevel level = (ServerLevel) helper.getLevel();
		ContraptionSimServerLevel sim = sim(helper, c);
		BlockState state = c.getBlocks().get(local).state();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(local), Direction.UP, local, false);
		state.useWithoutItem(sim, FakePlayerFactory.getMinecraft(level), hit);
	}

	private static BlockState state(Contraption c, BlockPos local) {
		StructureBlockInfo info = c.getBlocks().get(local);
		return info == null ? Blocks.AIR.defaultBlockState() : info.state();
	}

	private static boolean has(Contraption c, BlockPos local, net.minecraft.world.level.block.state.properties.Property<Boolean> prop) {
		BlockState s = state(c, local);
		return s.hasProperty(prop) && s.getValue(prop);
	}

	// endregion

	/** A lever, when flipped, powers an adjacent redstone lamp; flipping it back turns it off. */
	@GameTest(template = TEMPLATE)
	public static void leverTogglesPowerAndLamp(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos support = new BlockPos(2, 0, 0);
		BlockPos lever = new BlockPos(2, 1, 0);
		BlockPos lamp = new BlockPos(1, 1, 0);
		put(c, support, Blocks.STONE.defaultBlockState());
		put(c, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
		put(c, lever, Blocks.LEVER.defaultBlockState()
			.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

		use(helper, c, lever);
		if (!has(c, lever, BlockStateProperties.POWERED)) {
			helper.fail("Lever should be powered after first use");
			return;
		}
		if (!has(c, lamp, BlockStateProperties.LIT)) {
			helper.fail("Lamp should light immediately when the lever is switched on");
			return;
		}

		use(helper, c, lever);
		if (has(c, lever, BlockStateProperties.POWERED)) {
			helper.fail("Lever should be unpowered after the second use");
			return;
		}
		// The lamp turns off on a 4-tick scheduled tick; advance time, then pump the queue.
		helper.runAfterDelay(6, () -> {
			motor.serverTick();
			if (has(c, lamp, BlockStateProperties.LIT)) {
				helper.fail("Lamp should turn off after the lever is switched back off");
				return;
			}
			helper.succeed();
		});
	}

	/** A stone button powers a lamp when pressed and auto-releases after its scheduled tick. */
	@GameTest(template = TEMPLATE)
	public static void buttonPressAndAutoRelease(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos button = new BlockPos(0, 1, 0);
		BlockPos lamp = new BlockPos(1, 1, 0);
		put(c, new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
		put(c, button, Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

		use(helper, c, button);
		if (!has(c, button, BlockStateProperties.POWERED)) {
			helper.fail("Button should be powered immediately after being pressed");
			return;
		}
		if (!has(c, lamp, BlockStateProperties.LIT)) {
			helper.fail("Lamp should light while the button is pressed");
			return;
		}

		// Stone button stays pressed 20 ticks; wait past that and pump the scheduled release.
		helper.runAfterDelay(24, () -> {
			motor.serverTick();
			if (has(c, button, BlockStateProperties.POWERED)) {
				helper.fail("Button should have auto-released after its scheduled tick");
				return;
			}
			helper.succeed();
		});
	}

	/** A trapdoor opens (and closes) on right-click. */
	@GameTest(template = TEMPLATE)
	public static void trapdoorOpensOnUse(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos trapdoor = new BlockPos(0, 1, 0);
		put(c, trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState());

		use(helper, c, trapdoor);
		if (!has(c, trapdoor, BlockStateProperties.OPEN)) {
			helper.fail("Trapdoor should be open after the first use");
			return;
		}
		use(helper, c, trapdoor);
		if (has(c, trapdoor, BlockStateProperties.OPEN)) {
			helper.fail("Trapdoor should be closed again after the second use");
			return;
		}
		helper.succeed();
	}

	/** A wooden door opens on right-click and both halves follow. */
	@GameTest(template = TEMPLATE)
	public static void doorOpensOnUse(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos lower = new BlockPos(0, 1, 0);
		BlockPos upper = new BlockPos(0, 2, 0);
		put(c, new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, lower, Blocks.OAK_DOOR.defaultBlockState()
			.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		put(c, upper, Blocks.OAK_DOOR.defaultBlockState()
			.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

		use(helper, c, lower);
		if (!has(c, lower, BlockStateProperties.OPEN)) {
			helper.fail("Door lower half should be open after use");
			return;
		}
		helper.succeed();
	}

	/** A redstone torch standing on a block lights an adjacent lamp. */
	@GameTest(template = TEMPLATE)
	public static void redstoneTorchLightsLamp(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos torchBase = new BlockPos(0, 0, 0);
		BlockPos torch = new BlockPos(0, 1, 0);
		BlockPos lamp = new BlockPos(1, 1, 0);
		put(c, torchBase, Blocks.STONE.defaultBlockState());
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
		// Place the (lit) torch through the sim so it notifies the adjacent lamp.
		place(helper, c, torch, Blocks.REDSTONE_TORCH.defaultBlockState());

		if (!has(c, lamp, BlockStateProperties.LIT)) {
			helper.fail("Lamp adjacent to a lit redstone torch should be lit");
			return;
		}
		helper.succeed();
	}

	/** A redstone block placed next to a lamp lights it immediately (signal read, no scheduled tick). */
	@GameTest(template = TEMPLATE)
	public static void redstoneBlockLightsLampImmediately(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos lamp = new BlockPos(1, 1, 0);
		BlockPos source = new BlockPos(2, 1, 0);
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
		place(helper, c, source, Blocks.REDSTONE_BLOCK.defaultBlockState());

		if (!has(c, lamp, BlockStateProperties.LIT)) {
			helper.fail("Lamp next to a redstone block should be lit");
			return;
		}
		helper.succeed();
	}

	/** A repeater only powers its output after its configured tick delay. */
	@GameTest(template = TEMPLATE)
	public static void repeaterDelaysSignal(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos repeater = new BlockPos(1, 1, 0);
		// DiodeBlock reads its input from pos.relative(FACING): an east-facing repeater's input is its
		// east neighbour, output to the west. (POWERED reflects the output state.)
		BlockPos input = new BlockPos(2, 1, 0);
		put(c, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, repeater, Blocks.REPEATER.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

		// Energise the input; the repeater should NOT be powered on the same tick.
		place(helper, c, input, Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (has(c, repeater, BlockStateProperties.POWERED)) {
			helper.fail("Repeater should not power its output on the same tick (it has a delay)");
			return;
		}

		// After the 2-tick delay, the repeater output is powered.
		helper.runAfterDelay(4, () -> {
			motor.serverTick();
			if (!has(c, repeater, BlockStateProperties.POWERED)) {
				helper.fail("Repeater output should be powered after its delay elapses");
				return;
			}
			helper.succeed();
		});
	}

	/** A repeater's output powers an adjacent redstone wire (and a lamp under it). */
	@GameTest(template = TEMPLATE)
	public static void repeaterPowersDust(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		// East-facing repeater: input at its east neighbour, output to its west.
		BlockPos repeater = new BlockPos(1, 1, 0);
		BlockPos input = new BlockPos(2, 1, 0);
		BlockPos dust = new BlockPos(0, 1, 0);   // output side (west)
		BlockPos lamp = new BlockPos(0, 0, 0);   // dust sits on the lamp
		put(c, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
		put(c, dust, Blocks.REDSTONE_WIRE.defaultBlockState());
		put(c, repeater, Blocks.REPEATER.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

		place(helper, c, input, Blocks.REDSTONE_BLOCK.defaultBlockState());

		helper.runAfterDelay(4, () -> {
			motor.serverTick();
			if (!has(c, repeater, BlockStateProperties.POWERED)) {
				helper.fail("Repeater output should be powered after its delay");
				return;
			}
			int power = state(c, dust).getValue(BlockStateProperties.POWER);
			if (power <= 0) {
				helper.fail("Repeater should power the adjacent redstone wire; got power " + power);
				return;
			}
			if (!has(c, lamp, BlockStateProperties.LIT)) {
				helper.fail("Lamp under the powered wire should be lit");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * An unsupported falling block (sand) detaches into a real {@link FallingBlockEntity} rather than
	 * vanishing — the entity inherits the contraption's rotation/velocity (like a split-off group).
	 */
	@GameTest(template = TEMPLATE)
	public static void unsupportedFallingBlockSpawnsEntity(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		// Sand with air directly below it (unsupported), still 26-connected to the head at (-1,0,0).
		BlockPos sand = new BlockPos(0, 1, 0);
		place(helper, c, sand, Blocks.SAND.defaultBlockState());
		if (!state(c, sand).is(Blocks.SAND)) {
			helper.fail("Sand should be present before its scheduled fall tick");
			return;
		}

		helper.runAfterDelay(3, () -> {
			motor.serverTick();
			if (!state(c, sand).isAir()) {
				helper.fail("Unsupported sand should have detached off the contraption");
				return;
			}
			AABB area = AABB.ofSize(Vec3.atCenterOf(helper.absolutePos(MOTOR_POS)), 24, 24, 24);
			List<FallingBlockEntity> falling =
				((ServerLevel) helper.getLevel()).getEntitiesOfClass(FallingBlockEntity.class, area);
			if (falling.isEmpty()) {
				helper.fail("A FallingBlockEntity should have spawned for the detached sand");
				return;
			}
			helper.succeed();
		});
	}

	/** An observer emits a short power pulse when the block it watches changes. */
	@GameTest(template = TEMPLATE)
	public static void observerEmitsPulse(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos observer = new BlockPos(0, 1, 0);
		BlockPos watched = new BlockPos(1, 1, 0); // east of the observer
		put(c, observer, Blocks.OBSERVER.defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.EAST));

		// Change the watched cell through the sim so the shape update reaches the observer.
		place(helper, c, watched, Blocks.STONE.defaultBlockState());

		helper.runAfterDelay(2, () -> {
			motor.serverTick();
			if (!has(c, observer, BlockStateProperties.POWERED)) {
				helper.fail("Observer should pulse powered after the watched block changes");
				return;
			}
			// The pulse is brief; it should drop again shortly after.
			helper.runAfterDelay(4, () -> {
				motor.serverTick();
				if (has(c, observer, BlockStateProperties.POWERED)) {
					helper.fail("Observer pulse should end after a couple of ticks");
					return;
				}
				helper.succeed();
			});
		});
	}

	/** Redstone dust carries a signal across several cells to a distant lamp. */
	@GameTest(template = TEMPLATE)
	public static void dustPropagatesToDistantLamp(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		// Platform y=0 with a lamp at the far end; a dust line on top; a redstone block at the near end.
		put(c, new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
		put(c, new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState());
		BlockPos lamp = new BlockPos(3, 0, 0);
		put(c, lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

		put(c, new BlockPos(1, 1, 0), Blocks.REDSTONE_WIRE.defaultBlockState());
		put(c, new BlockPos(2, 1, 0), Blocks.REDSTONE_WIRE.defaultBlockState());
		put(c, new BlockPos(3, 1, 0), Blocks.REDSTONE_WIRE.defaultBlockState()); // sits on the lamp

		// Energise the near end of the dust line; the wire network powers across to the far lamp.
		place(helper, c, new BlockPos(0, 1, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());

		if (!has(c, lamp, BlockStateProperties.LIT)) {
			helper.fail("Lamp under the far end of an energised dust line should be lit");
			return;
		}
		helper.succeed();
	}
}
