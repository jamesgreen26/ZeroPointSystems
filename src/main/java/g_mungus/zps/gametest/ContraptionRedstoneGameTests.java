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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import g_mungus.zps.contraption.ContraptionTransform;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
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
		return new ContraptionSimServerLevel((ServerLevel) helper.getLevel(), c, MOTOR_POS, null, null, null);
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

	// region live block entities

	/**
	 * A furnace on the contraption smelts: live block entities are ticked by the motor, so raw iron
	 * + fuel becomes an iron ingot in the output slot. Drives {@code serverTick} synchronously many
	 * times (the furnace advances one step per tick) rather than waiting real game ticks.
	 */
	@GameTest(template = TEMPLATE)
	public static void furnaceSmeltsOnContraption(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos furnacePos = new BlockPos(0, 1, 0);
		place(helper, c, furnacePos, Blocks.FURNACE.defaultBlockState());

		Container furnace = (Container) motor.getContraptionBlockEntity(furnacePos);
		if (furnace == null) {
			helper.fail("Furnace should have a live block entity on the contraption");
			return;
		}
		furnace.setItem(0, new ItemStack(Items.RAW_IRON));
		furnace.setItem(1, new ItemStack(Items.COAL));

		// One smelt is ~200 furnace ticks; pump the motor enough times to finish it.
		for (int i = 0; i < 220; i++)
			motor.serverTick();

		ItemStack output = furnace.getItem(2);
		if (!output.is(Items.IRON_INGOT) || output.getCount() < 1) {
			helper.fail("Furnace should have smelted an iron ingot; output was " + output);
			return;
		}
		helper.succeed();
	}

	/** A hopper on the contraption pulls an item out of the chest above it. */
	@GameTest(template = TEMPLATE)
	public static void hopperPullsFromChestAbove(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos hopperPos = new BlockPos(0, 1, 0);
		BlockPos chestPos = new BlockPos(0, 2, 0);
		place(helper, c, hopperPos, Blocks.HOPPER.defaultBlockState());
		place(helper, c, chestPos, Blocks.CHEST.defaultBlockState());

		Container chest = (Container) motor.getContraptionBlockEntity(chestPos);
		Container hopper = (Container) motor.getContraptionBlockEntity(hopperPos);
		if (chest == null || hopper == null) {
			helper.fail("Chest and hopper should have live block entities");
			return;
		}
		chest.setItem(0, new ItemStack(Items.DIAMOND));

		// Hopper transfer cooldown is 8 ticks; pump a few more to be safe.
		for (int i = 0; i < 12; i++)
			motor.serverTick();

		if (!hopper.getItem(0).is(Items.DIAMOND)) {
			helper.fail("Hopper should have pulled the diamond from the chest above it");
			return;
		}
		if (!chest.getItem(0).isEmpty()) {
			helper.fail("Chest should be empty after the hopper pulled its contents");
			return;
		}
		helper.succeed();
	}

	/** A comparator reads the fullness of a chest on the contraption and powers its output. */
	@GameTest(template = TEMPLATE)
	public static void comparatorReadsChest(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();

		BlockPos comparator = new BlockPos(0, 1, 0);
		BlockPos chestPos = new BlockPos(1, 1, 0); // east of the comparator = its input/rear
		put(c, new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
		place(helper, c, chestPos, Blocks.CHEST.defaultBlockState());
		place(helper, c, comparator, Blocks.COMPARATOR.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

		Container chest = (Container) motor.getContraptionBlockEntity(chestPos);
		if (chest == null) {
			helper.fail("Chest should have a live block entity");
			return;
		}
		chest.setItem(0, new ItemStack(Items.STONE, 64));
		// Notify neighbours of the container's new output signal (comparator schedules a recompute).
		BlockEntity chestBE = motor.getContraptionBlockEntity(chestPos);
		chestBE.setChanged();

		helper.runAfterDelay(2, () -> {
			motor.serverTick();
			if (!has(c, comparator, BlockStateProperties.POWERED)) {
				helper.fail("Comparator should be powered while reading a filled chest");
				return;
			}
			helper.succeed();
		});
	}

	/** Live block-entity state survives a save/load round-trip (flush -> writeNBT -> readNBT). */
	@GameTest(template = TEMPLATE)
	public static void blockEntityStatePersists(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();
		ServerLevel level = (ServerLevel) helper.getLevel();

		BlockPos chestPos = new BlockPos(0, 1, 0);
		place(helper, c, chestPos, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) motor.getContraptionBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 5));

		// getUpdateTag flushes live BEs into the contraption, then serializes it.
		CompoundTag tag = motor.getUpdateTag(level.registryAccess()).getCompound("Contraption");
		Contraption reloaded = new Contraption();
		reloaded.readNBT(level.registryAccess(), tag, level.getGameTime());

		Container reloadedChest = (Container) sim(helper, reloaded).getBlockEntity(chestPos);
		if (reloadedChest == null || !reloadedChest.getItem(0).is(Items.DIAMOND)
			|| reloadedChest.getItem(0).getCount() != 5) {
			helper.fail("Reloaded chest should still contain 5 diamonds");
			return;
		}
		helper.succeed();
	}

	/**
	 * An entity a block/block-entity tick spawns into the sim level is redirected to the real outer
	 * level at the matching rotated world position (e.g. a dispenser's item, smelting XP), instead of
	 * being orphaned in the untracked sim level.
	 */
	@GameTest(template = TEMPLATE)
	public static void spawnedEntityRedirectedToRealLevel(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();
		ServerLevel level = (ServerLevel) helper.getLevel();

		ContraptionSimServerLevel sim = new ContraptionSimServerLevel(level, c, MOTOR_POS, null, null,
			() -> ContraptionTransform.ofCurrent(motor));
		Vec3 spawnLocal = Vec3.atCenterOf(new BlockPos(0, 1, 0));
		ItemEntity item = new ItemEntity(sim, spawnLocal.x, spawnLocal.y, spawnLocal.z, new ItemStack(Items.DIAMOND));
		sim.addFreshEntity(item);

		if (item.level() != level) {
			helper.fail("Spawned entity should have been re-homed to the real level");
			return;
		}
		Vec3 expected = ContraptionTransform.ofCurrent(motor).localToWorld(spawnLocal);
		AABB area = new AABB(BlockPos.containing(expected)).inflate(1.5);
		List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.DIAMOND));
		if (found.isEmpty()) {
			helper.fail("Diamond item entity should be in the real level near " + expected);
			return;
		}
		// The interpolation history must be snapped to the world pose (no visible lerp from the
		// contraption-local origin); moveTo -> setOldPosAndRot guarantees xOld == getX(), etc.
		if (item.xOld != item.getX() || item.yOld != item.getY() || item.zOld != item.getZ()) {
			helper.fail("Spawned entity's old position should be snapped to its world position");
			return;
		}
		helper.succeed();
	}

	/**
	 * A projectile (arrow) spawned into the sim level faces its (rotated) velocity using the projectile
	 * convention, with the rotation history snapped — so it doesn't appear mis-facing on the first
	 * client frame.
	 */
	@GameTest(template = TEMPLATE)
	public static void spawnedArrowFacesVelocity(GameTestHelper helper) {
		ServoMotorBlockEntity motor = setupMotor(helper);
		Contraption c = motor.getContraption();
		ServerLevel level = (ServerLevel) helper.getLevel();

		ContraptionSimServerLevel sim = new ContraptionSimServerLevel(level, c, MOTOR_POS, null, null,
			() -> ContraptionTransform.ofCurrent(motor));
		Vec3 spawnLocal = Vec3.atCenterOf(new BlockPos(0, 1, 0));
		Arrow arrow = new Arrow(sim, spawnLocal.x, spawnLocal.y, spawnLocal.z, ItemStack.EMPTY, null);
		arrow.setDeltaMovement(1.0, 0.0, 0.0); // local +X (east)
		arrow.setYRot(0.0F); // bogus initial facing, must be overridden from velocity
		arrow.setXRot(0.0F);
		sim.addFreshEntity(arrow);

		// angle 0 -> rotated velocity is still +X; projectile yaw = atan2(vx, vz) = atan2(1, 0) = 90.
		if (Math.abs(arrow.getYRot() - 90.0F) > 1.0F) {
			helper.fail("Arrow should face its velocity (yaw ~90), got " + arrow.getYRot());
			return;
		}
		if (arrow.yRotO != arrow.getYRot() || arrow.xRotO != arrow.getXRot()) {
			helper.fail("Arrow rotation history should be snapped to its facing");
			return;
		}
		helper.succeed();
	}

	// endregion
}
