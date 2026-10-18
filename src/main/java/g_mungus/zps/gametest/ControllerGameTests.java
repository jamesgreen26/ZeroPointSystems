package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.ControllerInteraction;
import g_mungus.zps.block.cableNetwork.OctoControllerBlock;
import g_mungus.zps.blockentity.OctoControllerBlockEntity;
import g_mungus.zps.entity.ControllerSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Riding an Octo-Controller: the screen lights while someone is on it, and sneaking does not get them off. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ControllerGameTests {

    /**
     * A mock player standing in this test. Made at the world origin, a rider's first tick would
     * otherwise pick up items from a box stretched between there and its vehicle, which on 1.20.1's
     * layout crosses other tests.
     */
    private static Player mockRider(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)));
        return player;
    }

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos CONTROLLER = new BlockPos(3, 2, 3);

    private static Player board(GameTestHelper helper) {
        BlockState state = ModBlocks.OCTO_CONTROLLER.get().defaultBlockState()
                .setValue(OctoControllerBlock.FACING, Direction.NORTH);
        helper.setBlock(CONTROLLER, state);
        Player player = mockRider(helper);
        if (!(helper.getBlockEntity(CONTROLLER) instanceof OctoControllerBlockEntity controller)) {
            helper.fail("No controller block entity");
            return player;
        }
        controller.startRiding(player, false, helper.absolutePos(CONTROLLER), state, helper.getLevel());
        if (!(player.getVehicle() instanceof ControllerSeatEntity)) {
            helper.fail("Using the controller should put the player on its seat");
        }
        return player;
    }

    private static boolean active(GameTestHelper helper) {
        return helper.getBlockState(CONTROLLER).getValue(ControllerInteraction.ACTIVE);
    }

    @GameTest(template = TEMPLATE)
    public static void theScreenLightsWhileRiddenAndGoesDarkAfter(GameTestHelper helper) {
        Player player = board(helper);
        helper.runAfterDelay(2, () -> {
            if (!active(helper)) {
                helper.fail("The controller should light up once someone is riding it");
            }
            player.stopRiding();
            helper.runAfterDelay(2, () -> {
                if (active(helper)) {
                    helper.fail("The controller should go dark once its rider gets off");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sneakingDoesNotGetTheRiderOff(GameTestHelper helper) {
        Player player = board(helper);
        helper.runAfterDelay(2, () -> {
            player.setShiftKeyDown(true);
            player.rideTick();
            if (!(player.getVehicle() instanceof ControllerSeatEntity)) {
                helper.fail("Sneaking should not take the rider off a controller");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void removingTheSeatPutsTheControllerOut(GameTestHelper helper) {
        Player player = board(helper);
        helper.runAfterDelay(2, () -> {
            if (!(player.getVehicle() instanceof ControllerSeatEntity seat)) {
                helper.fail("The player should still be on the seat");
                return;
            }
            seat.discard();
            if (active(helper)) {
                helper.fail("A seat taken away under its rider should leave the controller dark");
            }
            helper.succeed();
        });
    }
}
