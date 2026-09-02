package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.DuctConnectionType;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.GasNodeBlock;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * The duct's three face states: joined, sealed, and open to the air after something next to it was
 * blown up.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DuctGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos FIRST = new BlockPos(3, 2, 3);
    /** One block south of FIRST. */
    private static final BlockPos SECOND = new BlockPos(3, 2, 4);

    private static void placeDucts(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());
        helper.setBlock(SECOND, ModBlocks.GAS_DUCT.get().defaultBlockState());
    }

    private static DuctConnectionType south(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(GasNodeBlock.SOUTH_CONNECTION);
    }

    private static DuctConnectionType north(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(GasNodeBlock.NORTH_CONNECTION);
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static double gasAt(GameTestHelper helper, BlockPos relative) {
        double total = 0;
        for (double mass : KelvinMod.INSTANCE.forceGetKelvin()
                .getGasMassAt(node(helper, relative)).values()) {
            total += mass;
        }
        return total;
    }

    @GameTest(template = TEMPLATE)
    public static void joinedFacesReadAsConnectedAndTheRestStaySealed(GameTestHelper helper) {
        placeDucts(helper);

        if (south(helper, FIRST) != DuctConnectionType.CONNECTION) {
            helper.fail("The face toward the neighbour should be connected, was " + south(helper, FIRST));
        }
        if (north(helper, SECOND) != DuctConnectionType.CONNECTION) {
            helper.fail("The neighbour's facing side should be connected too, was " + north(helper, SECOND));
        }
        // Everything else has nothing to join to.
        if (north(helper, FIRST) != DuctConnectionType.NONE) {
            helper.fail("A face with no neighbour should be sealed, was " + north(helper, FIRST));
        }
        helper.succeed();
    }

    /** A row of ducts along the z axis, so a blast at one end leaves survivors at the other. */
    private static void placeRow(GameTestHelper helper) {
        for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(3, 2, z), ModBlocks.GAS_DUCT.get().defaultBlockState());
        }
    }

    private static void blastOneEnd(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.getLevel().explode(null, absolute.getX() + 0.5, absolute.getY() + 0.5,
                absolute.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.TNT);
    }

    /**
     * The first surviving duct whose neighbour toward the blast is gone, or -1. An explosion takes
     * out however many blocks it takes out, so the test finds the boundary rather than assuming it.
     */
    private static int firstSurvivorNextToTheBlast(GameTestHelper helper) {
        for (int z = 2; z <= 5; z++) {
            boolean standing = helper.getBlockState(new BlockPos(3, 2, z)).getBlock()
                    == ModBlocks.GAS_DUCT.get();
            boolean neighbourGone = helper.getBlockState(new BlockPos(3, 2, z - 1)).isAir();
            if (standing && neighbourGone) {
                return z;
            }
        }
        return -1;
    }

    @GameTest(template = TEMPLATE)
    public static void explodingADuctLeavesItsNeighbourOpen(GameTestHelper helper) {
        placeRow(helper);
        blastOneEnd(helper);

        int survivor = firstSurvivorNextToTheBlast(helper);
        if (survivor < 0) {
            helper.fail("The blast left no duct standing next to a destroyed one, so the test "
                    + "proves nothing");
            return;
        }

        DuctConnectionType facingTheBlast = north(helper, new BlockPos(3, 2, survivor));
        if (facingTheBlast != DuctConnectionType.LEAK) {
            helper.fail("A duct whose neighbour was blown up should be left open, was "
                    + facingTheBlast);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void puttingADuctBackSealsTheLeak(GameTestHelper helper) {
        placeRow(helper);
        blastOneEnd(helper);

        int survivor = firstSurvivorNextToTheBlast(helper);
        if (survivor < 0 || north(helper, new BlockPos(3, 2, survivor)) != DuctConnectionType.LEAK) {
            helper.fail("Setup failed: no duct was left open by the blast");
            return;
        }

        helper.setBlock(new BlockPos(3, 2, survivor - 1), ModBlocks.GAS_DUCT.get().defaultBlockState());

        DuctConnectionType sealed = north(helper, new BlockPos(3, 2, survivor));
        if (sealed != DuctConnectionType.CONNECTION) {
            helper.fail("Replacing the neighbour should close the leak, was " + sealed);
        }
        helper.succeed();
    }

    /** Gas put in one duct should reach the other, whichever way round they are. */
    private static void assertGasFlows(GameTestHelper helper, BlockPos from, BlockPos to,
                                       String describeDirection) {
        placeDucts(helper);
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, from), ModGases.FLUX, 1.0, 500.0);

        helper.runAfterDelay(20, () -> {
            double arrived = gasAt(helper, to);
            if (arrived <= 1e-6) {
                helper.fail("No gas flowed " + describeDirection + ": " + gasAt(helper, from)
                        + " kg stayed put and " + arrived + " kg arrived");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasFlowsTowardIncreasingCoordinates(GameTestHelper helper) {
        assertGasFlows(helper, FIRST, SECOND, "south (+z)");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasFlowsTowardDecreasingCoordinates(GameTestHelper helper) {
        // Kelvin's solver blocks flow from nodeB to nodeA on any edge implementing PumpEdge, and
        // canonical ordering puts the lower coordinate first — so an edge that implements PumpEdge
        // when it has no pump only ever flows one way, toward +x/+y/+z.
        assertGasFlows(helper, SECOND, FIRST, "north (-z)");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aDuctRebuildsItsNodeWhenItLoadsWithoutOne(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());

        // What a chunk load looks like from Kelvin's side: the block and its block entity are
        // there, but the network has no node for them. Kelvin never persists nodes — its chunk
        // save/load is commented out upstream — and nodePlace only runs when a block is placed.
        KelvinMod.INSTANCE.forceGetKelvin().removeNode(node(helper, FIRST));
        if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, FIRST)) != null) {
            helper.fail("Setup failed: the node was not removed");
            return;
        }

        if (!(helper.getBlockEntity(FIRST) instanceof GasNodeBlockEntity blockEntity)) {
            helper.fail("The duct has no gas node block entity");
            return;
        }
        blockEntity.onLoad();

        helper.runAfterDelay(5, () -> {
            if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, FIRST)) == null) {
                helper.fail("A duct that loaded without a node never rebuilt one, so its gas "
                        + "network is gone after any reload");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aRebuiltNodeKeepsTheGasThatWasSaved(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, FIRST), ModGases.FLUX, 0.75, 500.0);

        if (!(helper.getBlockEntity(FIRST) instanceof GasNodeBlockEntity blockEntity)) {
            helper.fail("The duct has no gas node block entity");
            return;
        }

        // Save the way a chunk save would, then reproduce a load: node gone, block entity restored
        // from its tag.
        CompoundTag tag = blockEntity.saveWithoutMetadata(helper.getLevel().registryAccess());
        KelvinMod.INSTANCE.forceGetKelvin().removeNode(node(helper, FIRST));
        blockEntity.loadWithComponents(tag, helper.getLevel().registryAccess());
        blockEntity.onLoad();

        helper.runAfterDelay(5, () -> {
            // Kelvin's loadData recreates the node *info* even with no node behind it, so checking
            // the gas alone would pass while the solver quietly ignores the position. The node has
            // to be back as well, and rebuilding it must not wipe what was restored.
            if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, FIRST)) == null) {
                helper.fail("The gas came back but the node did not, so nothing will simulate it");
                return;
            }
            double restored = gasAt(helper, FIRST);
            if (Math.abs(restored - 0.75) > 1e-3) {
                helper.fail("A reloaded duct should still hold the 0.75 kg it was saved with, "
                        + "found " + restored);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aLeakingDuctStartsTickingAndReportsItsLeakRate(GameTestHelper helper) {
        placeRow(helper);
        blastOneEnd(helper);

        int survivor = firstSurvivorNextToTheBlast(helper);
        if (survivor < 0) {
            helper.fail("Setup failed: the blast left no duct open");
            return;
        }
        BlockPos leaking = new BlockPos(3, 2, survivor);
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, leaking), ModGases.FLUX, 2.0, 500.0);

        helper.runAfterDelay(6, () -> {
            if (!(helper.getBlockEntity(leaking) instanceof GasNodeBlockEntity blockEntity)) {
                helper.fail("The leaking duct has no block entity");
                return;
            }
            // Only a tick can send this, and a duct only gets a ticker once it is leaking — so a
            // non-zero figure proves both that the ticker attached when the face opened and that
            // the escape rate is being reported for the plume to be drawn from.
            if (blockEntity.getLastSentMass() <= 0) {
                helper.fail("A leaking duct never reported an escape rate, so no plume would be "
                        + "drawn; last sent " + blockEntity.getLastSentMass());
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anIntactDuctNeverTicks(GameTestHelper helper) {
        placeDucts(helper);
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, FIRST), ModGases.FLUX, 1.0, 500.0);

        helper.runAfterDelay(20, () -> {
            if (!(helper.getBlockEntity(FIRST) instanceof GasNodeBlockEntity blockEntity)) {
                helper.fail("The duct has no block entity");
                return;
            }
            // Sealed ducts carry no ticker at all, so they cost nothing in a large network.
            if (blockEntity.getLastSentMass() != 0) {
                helper.fail("An intact duct is ticking when it has nothing to draw");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anOpenDuctBleedsItsGasAway(GameTestHelper helper) {
        helper.setBlock(FIRST, ModBlocks.GAS_DUCT.get().defaultBlockState());
        // Force one face open without needing an explosion, so the test is about the leak itself.
        BlockState leaking = helper.getBlockState(FIRST)
                .setValue(GasNodeBlock.SOUTH_CONNECTION, DuctConnectionType.LEAK);
        helper.setBlock(FIRST, leaking);

        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, FIRST), ModGases.FLUX, 1.0, 500.0);
        double before = gasAt(helper, FIRST);
        if (before <= 0) {
            helper.fail("Setup failed: no gas in the duct");
            return;
        }

        helper.runAfterDelay(40, () -> {
            double after = gasAt(helper, FIRST);
            if (after >= before) {
                helper.fail("An open duct kept its gas: " + before + " kg -> " + after + " kg");
            }
            helper.succeed();
        });
    }
}
