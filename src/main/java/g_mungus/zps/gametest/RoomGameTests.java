package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.LargeSpaceDoorBlock;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sealed rooms: one forms where gas goes into a space air cannot leave, replaces the atmosphere for
 * anything inside, and is gone the moment a hole opens — a wall broken, a door opened.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RoomGameTests {

    /**
     * The framework walls and roofs every arena in barriers, which seal the air around the shell
     * into one big room. Tests that open a hole take the roof off ({@code skyAccess}) so the air
     * has somewhere to go.
     */
    private static final String TEMPLATE = "gametest/flat_11x9x11";

    /** Stone shell; the air inside is 3x3x3, from MIN + 1 to MAX - 1. */
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);
    private static final BlockPos CENTRE = new BlockPos(5, 3, 5);
    private static final BlockPos FLOOR_CELL = new BlockPos(5, 2, 5);
    private static final BlockPos WEST_WALL = new BlockPos(3, 3, 5);

    private static final ResourceLocation FLUX = ModGases.FLUX.getResourceLocation();

    private static void buildShell(GameTestHelper helper) {
        for (int x = MIN.getX(); x <= MAX.getX(); x++) {
            for (int y = MIN.getY(); y <= MAX.getY(); y++) {
                for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                    boolean wall = x == MIN.getX() || x == MAX.getX() || y == MIN.getY() || y == MAX.getY()
                            || z == MIN.getZ() || z == MAX.getZ();
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // The test world is reused between runs, and a room saved by an earlier run would still be here.
        Room stale = manager(helper).knownRoomAt(helper.absolutePos(CENTRE));
        if (stale != null) {
            manager(helper).dissolve(stale);
        }
    }

    private static RoomManager manager(GameTestHelper helper) {
        return RoomManager.get(helper.getLevel());
    }

    private static void addFlux(GameTestHelper helper, BlockPos at, double kg) {
        Map<GasType, Double> gas = Map.of(ModGases.FLUX, kg);
        manager(helper).addGas(helper.getLevel(), helper.absolutePos(at), gas, 300.0);
    }

    private static Room roomAt(GameTestHelper helper, BlockPos relative) {
        return manager(helper).roomAt(helper.absolutePos(relative));
    }

    /** Gas into a sealed box makes it a room: the outside air it held plus what went in. */
    @GameTest(template = TEMPLATE)
    public static void gasIntoSealedBox_makesARoom(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);

        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(room != null, "Gas into a sealed box should make a room");
        helper.assertTrue(room.volume() == 27, "The room should be the 27 cells inside, is " + room.volume());
        helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) == 5.0, "The room should hold the flux put in");
        helper.assertTrue(room.massKg().getOrDefault(AtmosphericComposition.AIR, 0.0) > 0,
                "The room should hold the air it sealed in");
        helper.succeed();
    }

    /** Whoever is inside breathes the room, not the biome. */
    @GameTest(template = TEMPLATE)
    public static void room_replacesTheAtmosphere(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        Chicken chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, FLOOR_CELL);

        Map<ResourceLocation, Double> fractions = AtmosphereExposure.atmosphere(chicken).fractions();
        helper.assertTrue(fractions.getOrDefault(FLUX, 0.0) > 0, "The chicken should be in the room's flux, has " + fractions);
        helper.succeed();
    }

    /** Without gas going in there is nothing to replace the outside with, so no room. */
    @GameTest(template = TEMPLATE)
    public static void sealedBoxWithoutGas_isNoRoom(GameTestHelper helper) {
        buildShell(helper);
        helper.assertTrue(roomAt(helper, CENTRE) == null, "No gas went in, so there should be no room");
        helper.succeed();
    }

    /** Seals the shell with {@code state} in the west wall, puts gas in, and says whether a room formed. */
    private static boolean sealsWith(GameTestHelper helper, BlockPos at, BlockState state) {
        buildShell(helper);
        helper.setBlock(at, state);
        addFlux(helper, CENTRE, 5.0);
        return roomAt(helper, CENTRE) != null;
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void copperGrateInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState()),
                "A copper grate should let the air out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void spaceGratingInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, ModBlocks.SPACE_GRATING_BLOCK.get().defaultBlockState()),
                "Space grating should let the air out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void spaceTrussInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, ModBlocks.SPACE_TRUSS.get().defaultBlockState()),
                "A space truss should let the air out");
        helper.succeed();
    }

    /** A closed gate is a plate across the gap, which would seal but for being a gate. */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void closedFenceGateInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(FenceGateBlock.FACING, Direction.EAST)),
                "A closed fence gate should let the air out");
        helper.succeed();
    }

    /** A fence joined along the wall is a plate across the gap too. */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void fenceInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, Blocks.OAK_FENCE.defaultBlockState()
                        .setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true)),
                "A fence should let the air out");
        helper.succeed();
    }

    /** A catwalk is a full floor plate, which would seal a ceiling but for being mesh. */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void catwalkInTheCeiling_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, CENTRE.above(2), ModBlocks.CATWALK.get().defaultBlockState()),
                "A catwalk should let the air out");
        helper.succeed();
    }

    /** A cable boxed in grating is grating as far as the air goes; one in solid insulation still seals. */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void gratingCableInTheWall_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, WEST_WALL, ModBlocks.CABLE.get().defaultBlockState()
                        .setValue(CableBlock.INSULATION_TYPE, InsulationType.GRATING)),
                "A cable in grating should let the air out");
        helper.succeed();
    }

    /** A cable under a catwalk has the catwalk's full top surface, and lets air through as the catwalk does. */
    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void catwalkCableInTheCeiling_letsAirOut(GameTestHelper helper) {
        helper.assertFalse(sealsWith(helper, CENTRE.above(2), ModBlocks.CABLE.get().defaultBlockState()
                        .setValue(CableBlock.INSULATION_TYPE, InsulationType.CATWALK)),
                "A cable under a catwalk should let the air out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void insulatedCableInTheWall_seals(GameTestHelper helper) {
        helper.assertTrue(sealsWith(helper, WEST_WALL, ModBlocks.CABLE.get().defaultBlockState()
                        .setValue(CableBlock.INSULATION_TYPE, InsulationType.INSULATION)),
                "A cable in solid insulation should seal like any full block");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void ironBarsInTheWall_letAirOut(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL, Blocks.IRON_BARS.defaultBlockState());
        addFlux(helper, CENTRE, 5.0);

        helper.assertTrue(roomAt(helper, CENTRE) == null, "Iron bars should let the air out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void slabInTheWall_letsAirOut(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        addFlux(helper, CENTRE, 5.0);

        helper.assertTrue(roomAt(helper, CENTRE) == null, "Half a wall should let the air out over it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void slabInside_stillSeals(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(FLOOR_CELL, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        addFlux(helper, CENTRE, 5.0);

        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(room != null, "A slab inside should not stop the room sealing");
        helper.assertTrue(room.volume() == 27, "The slab's cell is still air above it, so the room is 27 cells, is " + room.volume());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, skyAccess = true)
    public static void breakingAWall_breaksTheRoom(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        helper.assertTrue(roomAt(helper, CENTRE) != null, "The room should have formed");

        helper.destroyBlock(WEST_WALL);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == null, "A broken wall should break the room");
            helper.succeed();
        });
    }

    /** A block placed inside changes nothing about the seal; the room is checked and stays. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void blockPlacedInside_keepsTheRoom(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);

        helper.setBlock(FLOOR_CELL, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = roomAt(helper, CENTRE);
            helper.assertTrue(room != null, "A block inside should not break the room");
            helper.assertTrue(room.volume() == 26, "The room should have shrunk by the block, is " + room.volume());
            // Less only the little that has leaked out since.
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 4.99, "The room should keep its gas");
            helper.succeed();
        });
    }

    /** Doors open without a neighbour update, so this is the path that has to catch every change. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, skyAccess = true)
    public static void openingADoor_breaksTheRoom(GameTestHelper helper) {
        buildShell(helper);
        BlockPos lower = new BlockPos(3, 2, 5);
        BlockPos upper = lower.above();
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        addFlux(helper, CENTRE, 5.0);
        helper.assertTrue(roomAt(helper, CENTRE) != null, "A closed door should seal");

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(lower);
        ((DoorBlock) Blocks.IRON_DOOR).setOpen(null, level, level.getBlockState(absolute), absolute, true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == null, "An open door should break the room");
            helper.succeed();
        });
    }

    /**
     * Fills the shell's whole north wall (z = 3, x 4..6, y 2..4) with a Large Space Door. Its closed
     * plate is inset a pixel from the cell's face, so it touches neither face of the cell it is in.
     */
    private static void placeLargeSpaceDoor(GameTestHelper helper, boolean open) {
        BlockPos controller = new BlockPos(5, 2, 3);
        BlockState door = ModBlocks.LARGE_SPACE_DOOR.get().defaultBlockState()
                .setValue(LargeSpaceDoorBlock.FACING, Direction.NORTH)
                .setValue(LargeSpaceDoorBlock.OPEN, open);
        for (LargeSpaceDoorBlock.Part part : LargeSpaceDoorBlock.Part.values()) {
            helper.setBlock(LargeSpaceDoorBlock.partPos(controller, door, part), door.setValue(LargeSpaceDoorBlock.PART, part));
        }
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void closedLargeSpaceDoor_seals(GameTestHelper helper) {
        buildShell(helper);
        placeLargeSpaceDoor(helper, false);
        addFlux(helper, CENTRE, 5.0);

        helper.assertTrue(roomAt(helper, CENTRE) != null, "A closed Large Space Door should seal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, skyAccess = true)
    public static void openingALargeSpaceDoor_breaksTheRoom(GameTestHelper helper) {
        buildShell(helper);
        placeLargeSpaceDoor(helper, false);
        addFlux(helper, CENTRE, 5.0);
        helper.assertTrue(roomAt(helper, CENTRE) != null, "A closed Large Space Door should seal");

        placeLargeSpaceDoor(helper, true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == null, "An open Large Space Door should break the room");
            helper.succeed();
        });
    }

    /**
     * Blocks placed inside one after another, each flooding the room afresh, never break a room
     * walled by a closed Large Space Door: a fill started in a door cell would take in its outside
     * pocket too.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, skyAccess = true)
    public static void blocksPlacedBesideALargeSpaceDoor_keepTheRoom(GameTestHelper helper) {
        buildShell(helper);
        placeLargeSpaceDoor(helper, false);
        addFlux(helper, CENTRE, 5.0);
        helper.assertTrue(roomAt(helper, CENTRE) != null, "A closed Large Space Door should seal");

        List<BlockPos> cells = new ArrayList<>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                cells.add(new BlockPos(x, 2, z));
            }
        }
        placeInTurn(helper, cells, 0);
    }

    private static void placeInTurn(GameTestHelper helper, List<BlockPos> cells, int index) {
        if (index == cells.size()) {
            helper.succeed();
            return;
        }
        helper.setBlock(cells.get(index), Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) != null, "A block placed at " + cells.get(index) + " broke the room");
            placeInTurn(helper, cells, index + 1);
        });
    }

    /** Mining into a thick wall grows the room by the hole: the same room, its gas kept. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, skyAccess = true)
    public static void miningIntoAThickWall_growsTheRoom(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL.west(), Blocks.STONE);
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);

        helper.destroyBlock(WEST_WALL);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == room, "The room should still be the same room");
            helper.assertTrue(room.volume() == 28, "The room should have grown by the hole, is " + room.volume());
            helper.assertTrue(roomAt(helper, WEST_WALL) == room, "The hole should be part of the room");
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 4.99, "The room should keep its gas");
            helper.succeed();
        });
    }

    /**
     * The room holds only its own side of a closed Large Space Door, so clearing a block on the far
     * side changes nothing; with the roof off, taking the far side for the room's would breach it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, skyAccess = true)
    public static void clearingPastAClosedLargeSpaceDoor_leavesTheRoom(GameTestHelper helper) {
        buildShell(helper);
        placeLargeSpaceDoor(helper, false);
        BlockPos outside = new BlockPos(5, 3, 2);
        helper.setBlock(outside, Blocks.STONE);
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(room != null, "A closed Large Space Door should seal");
        int volume = room.volume();

        helper.destroyBlock(outside);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == room, "The room should be untouched");
            helper.assertTrue(room.volume() == volume, "The room should not have grown, is " + room.volume());
            helper.succeed();
        });
    }

    /** End to end: a vent set in the wall, fed from outside, fills the room. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ventInTheWall_fillsTheRoom(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL, ModBlocks.VENT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        BlockPos source = WEST_WALL.west();
        helper.setBlock(source, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        if (!(helper.getBlockEntity(source) instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("The gas source has no block entity");
            return;
        }
        generator.setSettings(FLUX, 0.005, 400.0);

        helper.succeedWhen(() -> {
            Room room = roomAt(helper, CENTRE);
            helper.assertTrue(room != null, "The vent should have sealed the room");
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 0, "The room should be filling with flux");
        });
    }

    /**
     * A vent's plate is slim, so its gas comes out into its own cell. One set in the ceiling with
     * its mouth to the stone beside it still fills the room its cell opens on below.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ventFacingAWall_fillsTheRoomItsCellOpensOn(GameTestHelper helper) {
        buildShell(helper);
        BlockPos vent = CENTRE.above(2);
        helper.setBlock(vent.above(), Blocks.STONE.defaultBlockState());
        helper.setBlock(vent, ModBlocks.VENT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos source = vent.south();
        helper.setBlock(source, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        if (!(helper.getBlockEntity(source) instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("The gas source has no block entity");
            return;
        }
        generator.setSettings(FLUX, 0.005, 400.0);

        helper.succeedWhen(() -> {
            Room room = roomAt(helper, CENTRE);
            helper.assertTrue(room != null, "The vent should have sealed the room below it");
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 0, "The room should be filling with flux");
        });
    }

    /**
     * A vent set in the wall with open air behind it: its plate seals the hole, so the gas stays in
     * the room it faces and the room does not reach the cell behind.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, skyAccess = true)
    public static void ventInTheWall_sealsAgainstWhatIsBehindIt(GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(WEST_WALL, ModBlocks.VENT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        BlockPos behind = WEST_WALL.west();
        helper.setBlock(behind, Blocks.AIR.defaultBlockState());
        KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(
                GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(WEST_WALL)), ModGases.FLUX, 1.0, 300.0);

        helper.succeedWhen(() -> {
            Room room = roomAt(helper, CENTRE);
            helper.assertTrue(room != null, "The vent's plate should have sealed the hole it sits in");
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 0, "The vented flux should be in the room");
            helper.assertTrue(!room.interior().contains(helper.absolutePos(behind).asLong()),
                    "The room should stop at the vent's plate, not take in the cell behind it");
        });
    }

    /** Flux drains out through the walls and the outside air seeps back in. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void room_leaksBackTowardTheOutside(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(room != null, "The room should have formed");

        helper.runAfterDelay(20, () -> {
            double flux = room.massKg().getOrDefault(FLUX, 0.0);
            helper.assertTrue(flux < 5.0, "Flux should be leaking out, still " + flux);
            helper.assertTrue(flux > 4.5, "A 3x3x3 room should take about a minute to lose half, already down to " + flux);
            helper.succeed();
        });
    }

    /** More wall for the same air leaks faster: the 3x3x3 shell has 54 wall blocks around 27 of air. */
    @GameTest(template = TEMPLATE)
    public static void leakRate_followsSurfaceArea(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(room.surfaceArea() == 54, "The room should have 54 wall blocks, has " + room.surfaceArea());
        double fraction = RoomManager.leakFraction(room, 1.0e-3);
        helper.assertTrue(Math.abs(fraction - 2.0e-3) < 1e-12, "54 wall over 27 air at 1e-3 should leak 2e-3 a tick, is " + fraction);
        helper.succeed();
    }

    /**
     * A trickle too small to outpace the leak never makes the room any different from the outside,
     * so it stays dormant — and stays the same room, found once, not searched for on every puff.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void trickle_doesNotSustainARoom(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 1.0e-4);
        Room first = manager(helper).knownRoomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(first != null, "The room should have been found");
        for (int tick = 1; tick <= 40; tick++) {
            helper.runAfterDelay(tick, () -> addFlux(helper, CENTRE, 1.0e-4));
        }

        helper.runAfterDelay(45, () -> {
            helper.assertTrue(roomAt(helper, CENTRE) == null, "A trickle should not keep the room going");
            Room known = manager(helper).knownRoomAt(helper.absolutePos(CENTRE));
            helper.assertTrue(known == first, "The dormant room should have been woken, not searched for again");
            helper.succeed();
        });
    }

    /** A supply that outpaces the leak keeps the room going. */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void strongSupply_sustainsARoom(GameTestHelper helper) {
        buildShell(helper);
        for (int tick = 0; tick <= 40; tick++) {
            helper.runAfterDelay(tick, () -> addFlux(helper, CENTRE, 0.05));
        }

        helper.runAfterDelay(41, () -> {
            Room room = roomAt(helper, CENTRE);
            helper.assertTrue(room != null, "A strong supply should keep the room going");
            helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) > 1.0, "The flux should have built up");
            helper.succeed();
        });
    }

    private static GasGaugeBlockEntity placeGauge(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.GAS_GAUGE.get().defaultBlockState().setValue(GasGaugeBlock.FACING, facing));
        if (!(helper.getBlockEntity(pos) instanceof GasGaugeBlockEntity gauge)) {
            helper.fail("The gauge has no block entity");
            throw new IllegalStateException();
        }
        return gauge;
    }

    /** A gauge on nothing reads the outside air: about an atmosphere, at the biome's temperature. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void looseGauge_readsTheAtmosphere(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, new BlockPos(1, 2, 1), Direction.UP);

        helper.runAfterDelay(2, () -> {
            double pressure = gauge.getPressure();
            helper.assertTrue(pressure > 80_000 && pressure < 130_000, "A loose gauge should read about an atmosphere, reads " + pressure + " Pa");
            double temperature = gauge.getTemperature();
            helper.assertTrue(temperature > 200 && temperature < 330, "A loose gauge should read the biome's temperature, reads " + temperature + " K");
            helper.succeed();
        });
    }

    /** A gauge on a room's wall reads the room: flux packed in on top of the air raises the pressure. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void looseGaugeInARoom_readsTheRoom(GameTestHelper helper) {
        buildShell(helper);
        // Against the west wall, dial facing in, inlet on the stone.
        GasGaugeBlockEntity gauge = placeGauge(helper, WEST_WALL.east(), Direction.EAST);
        addFlux(helper, CENTRE, 50.0);
        helper.assertTrue(roomAt(helper, WEST_WALL.east()) != null, "The gauge's cell should be in the room");
        double outside = manager(helper).ambientAt(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1))).pressurePa();

        helper.runAfterDelay(2, () -> {
            double pressure = gauge.getPressure();
            helper.assertTrue(pressure > outside * 1.5, "The room is packed with flux, so it should read well over the outside's "
                    + outside + " Pa, reads " + pressure + " Pa");
            helper.succeed();
        });
    }

    /**
     * Pumps flux into the room to about {@code targetPa}. Pressure goes up in step with each gas's
     * mass, so one kilogram shows how much the rest needs to be.
     */
    private static Room pumpTo(GameTestHelper helper, double targetPa) {
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);
        double before = RoomManager.pressureOf(room);
        addFlux(helper, CENTRE, 1.0);
        double perKg = RoomManager.pressureOf(room) - before;
        addFlux(helper, CENTRE, (targetPa - RoomManager.pressureOf(room)) / perKg);
        return room;
    }

    /** Below 1 MPa everything put in stays in. */
    @GameTest(template = TEMPLATE)
    public static void lowPressureRoom_keepsWhatGoesIn(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        Room room = roomAt(helper, CENTRE);
        helper.assertTrue(RoomManager.pressureOf(room) < RoomManager.RELEASE_START_PA, "The room should start below 1 MPa");
        double before = room.totalMassKg();
        addFlux(helper, CENTRE, 5.0);
        helper.assertTrue(Math.abs(room.totalMassKg() - (before + 5.0)) < 1e-9, "All of it should go in");
        helper.succeed();
    }

    /** Between 1 and 10 MPa the share let back out rises linearly with the pressure. */
    @GameTest(template = TEMPLATE)
    public static void midPressureRoom_letsOutAShareOfWhatGoesIn(GameTestHelper helper) {
        buildShell(helper);
        Room room = pumpTo(helper, 5.5e6);
        double pressure = RoomManager.pressureOf(room);
        helper.assertTrue(pressure > 2e6 && pressure < 9e6, "The room should be between 1 and 10 MPa, is at " + pressure + " Pa");
        double share = (pressure - 1e6) / 9e6;

        double before = room.totalMassKg();
        addFlux(helper, CENTRE, 1.0);
        double kept = room.totalMassKg() - before;
        helper.assertTrue(Math.abs(kept - (1.0 - share)) < 1e-6,
                "At " + pressure + " Pa, " + (1.0 - share) + " kg of 1 should stay in, " + kept + " kg did");
        helper.succeed();
    }

    /** Past 10 MPa as much is let out as goes in: the mass holds, though what goes in still mixes through. */
    @GameTest(template = TEMPLATE)
    public static void maxPressureRoom_letsOutAllThatGoesIn(GameTestHelper helper) {
        buildShell(helper);
        Room room = pumpTo(helper, 1.2e7);
        helper.assertTrue(RoomManager.pressureOf(room) >= RoomManager.MAX_PRESSURE_PA,
                "The room should be over 10 MPa, is at " + RoomManager.pressureOf(room) + " Pa");

        double before = room.totalMassKg();
        double fluxBefore = room.massKg().getOrDefault(FLUX, 0.0);
        manager(helper).addGas(helper.getLevel(), helper.absolutePos(CENTRE), Map.of(ModGases.STEAM, 1.0), 300.0);
        double after = room.totalMassKg();
        helper.assertTrue(Math.abs(after - before) < 1e-9 * before, "The room's mass should hold, went from " + before + " to " + after + " kg");
        helper.assertTrue(room.massKg().getOrDefault(ModGases.STEAM.getResourceLocation(), 0.0) > 0.9,
                "The steam should mix through, less its share of what was let out");
        helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) < fluxBefore, "Some of the flux should have been let out");
        helper.succeed();
    }

    private static double buoyancyOfAir(GameTestHelper helper, BlockPos into, double temperatureK) {
        return manager(helper).buoyancy(helper.getLevel(), helper.absolutePos(into),
                Map.of(AtmosphericComposition.AIR, 1.0), temperatureK);
    }

    /** Air let out into air floats only by its temperature: hotter rises, colder sinks, the same neither. */
    @GameTest(template = TEMPLATE)
    public static void airIntoAir_floatsByTemperature(GameTestHelper helper) {
        BlockPos open = new BlockPos(1, 2, 1);
        double outsideK = AtmosphereExposure.temperatureK(helper.getLevel(), helper.absolutePos(open));
        double same = buoyancyOfAir(helper, open, outsideK);
        helper.assertTrue(Math.abs(same) < 1e-6, "Air at the air's own temperature should neither float nor sink, has " + same);
        helper.assertTrue(buoyancyOfAir(helper, open, outsideK * 2) > 0.4, "Hot air should rise");
        helper.assertTrue(buoyancyOfAir(helper, open, outsideK / 2) < -0.4, "Cold air should sink");
        helper.succeed();
    }

    /** Flux is far lighter than air, so it rises hard out in the open. */
    @GameTest(template = TEMPLATE)
    public static void fluxIntoAir_rises(GameTestHelper helper) {
        BlockPos open = new BlockPos(1, 2, 1);
        double outsideK = AtmosphereExposure.temperatureK(helper.getLevel(), helper.absolutePos(open));
        double buoyancy = manager(helper).buoyancy(helper.getLevel(), helper.absolutePos(open), Map.of(FLUX, 1.0), outsideK);
        helper.assertTrue(buoyancy > 0.5, "Flux should rise hard in air, has " + buoyancy);
        helper.succeed();
    }

    /** What counts is what is in the room: air let into a room full of flux is the heavy one, and sinks. */
    @GameTest(template = TEMPLATE)
    public static void airIntoAFluxRoom_sinks(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 500.0);
        Room room = roomAt(helper, CENTRE);
        double buoyancy = buoyancyOfAir(helper, CENTRE, room.temperatureK());
        helper.assertTrue(buoyancy < -0.5, "Air should sink in a room full of flux, has " + buoyancy);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void room_survivesASaveAndLoad(GameTestHelper helper) {
        buildShell(helper);
        addFlux(helper, CENTRE, 5.0);
        ServerLevel level = helper.getLevel();

        CompoundTag saved = manager(helper).save(new CompoundTag(), level.registryAccess());
        RoomManager loaded = RoomManager.load(saved, level.registryAccess());
        Room room = loaded.roomAt(helper.absolutePos(CENTRE));
        helper.assertTrue(room != null, "The room should load back");
        helper.assertTrue(room.volume() == 27, "The room should load with its cells");
        helper.assertTrue(room.massKg().getOrDefault(FLUX, 0.0) == 5.0, "The room should load with its gas");
        helper.succeed();
    }

    /** Farmland in the floor, dry, with {@code gas} put into the room above it; how wet one random tick leaves it. */
    private static int moistureAfterGasAbove(GameTestHelper helper, GasType gas) {
        buildShell(helper);
        BlockPos farmland = FLOOR_CELL.below();
        helper.setBlock(farmland, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        manager(helper).addGas(helper.getLevel(), helper.absolutePos(CENTRE), Map.of(gas, 5.0), 300.0);

        BlockPos at = helper.absolutePos(farmland);
        helper.getLevel().getBlockState(at).randomTick(helper.getLevel(), at, helper.getLevel().random);
        BlockState after = helper.getLevel().getBlockState(at);
        return after.is(Blocks.FARMLAND) ? after.getValue(FarmBlock.MOISTURE) : -1;
    }

    /** Steam against farmland waters it as well as water beside it would. */
    @GameTest(template = TEMPLATE)
    public static void steamAgainstFarmland_hydratesIt(GameTestHelper helper) {
        int moisture = moistureAfterGasAbove(helper, ModGases.STEAM);
        helper.assertTrue(moisture == FarmBlock.MAX_MOISTURE, "Farmland under steam should be fully wet, is " + moisture);
        helper.succeed();
    }

    /** Any other gas does nothing for it. */
    @GameTest(template = TEMPLATE)
    public static void fluxAgainstFarmland_leavesItDry(GameTestHelper helper) {
        int moisture = moistureAfterGasAbove(helper, ModGases.FLUX);
        helper.assertTrue(moisture <= 0, "Farmland under flux should stay dry, is " + moisture);
        helper.succeed();
    }

    /** Ice in the floor with flux at {@code temperatureK} filling the room above it; what one random tick leaves. */
    private static BlockState iceAfterGasAbove(GameTestHelper helper, double temperatureK) {
        buildShell(helper);
        BlockPos ice = FLOOR_CELL.below();
        helper.setBlock(ice, Blocks.ICE.defaultBlockState());
        manager(helper).addGas(helper.getLevel(), helper.absolutePos(CENTRE), Map.of(ModGases.FLUX, 500.0), temperatureK);

        BlockPos at = helper.absolutePos(ice);
        helper.getLevel().getBlockState(at).randomTick(helper.getLevel(), at, helper.getLevel().random);
        return helper.getLevel().getBlockState(at);
    }

    /** Gas hot enough to burn a player melts ice it touches. */
    @GameTest(template = TEMPLATE)
    public static void hotGasAgainstIce_meltsIt(GameTestHelper helper) {
        BlockState after = iceAfterGasAbove(helper, ZPSConfig.entityBurnGasTemperatureK() * 2);
        helper.assertTrue(after.is(Blocks.WATER), "Ice under hot gas should melt, is " + after);
        helper.succeed();
    }

    /** Gas at room temperature leaves it be. */
    @GameTest(template = TEMPLATE)
    public static void coolGasAgainstIce_leavesIt(GameTestHelper helper) {
        BlockState after = iceAfterGasAbove(helper, 300.0);
        helper.assertTrue(after.is(Blocks.ICE), "Ice under cool gas should stay ice, is " + after);
        helper.succeed();
    }
}
