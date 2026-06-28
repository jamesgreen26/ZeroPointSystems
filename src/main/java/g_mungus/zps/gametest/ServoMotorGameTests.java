package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.ServoMotorBlock;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ServoMotorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos MOTOR_POS = new BlockPos(2, 2, 2);
    private static final BlockPos FRONT_POS = MOTOR_POS.east();

    @GameTest(template = TEMPLATE)
    public static void placesPreAssembledWithHeadOnly(GameTestHelper helper) {
        helper.setBlock(MOTOR_POS, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
                .setValue(ServoMotorBlock.FACING, Direction.EAST));
        // A block in front must NOT be captured: there is no flood-fill assembly anymore.
        helper.setBlock(FRONT_POS, Blocks.STONE);

        BlockEntity be = helper.getBlockEntity(MOTOR_POS);
        if (!(be instanceof ServoMotorBlockEntity motor)) {
            helper.fail("Expected a ServoMotorBlockEntity at " + MOTOR_POS + ", got " + be);
            return;
        }

        motor.initContraption();
        Contraption contraption = motor.getContraption();
        if (contraption == null) {
            helper.fail("Motor should be pre-assembled with a contraption");
            return;
        }
        if (contraption.getBlocks().size() != 1) {
            helper.fail("Contraption should contain only the head; got " + contraption.getBlocks().size());
            return;
        }
        StructureBlockInfo head = contraption.getBlocks().get(motor.headLocalPos());
        if (head == null || !head.state().is(ModBlocks.SERVO_MOTOR_HEAD.get())) {
            helper.fail("Head should sit at the motor's own cell (local " + motor.headLocalPos() + ")");
            return;
        }
        // The block in front stays in the world untouched.
        helper.assertBlockPresent(Blocks.STONE, FRONT_POS);
        helper.succeed();
    }

    /**
     * Exercises the in-flight editing primitives on the contraption data model used to build
     * outward off the head (the server break/place methods wrap these but need a real
     * ServerPlayer, verified with runClient). Here we assert add/remove keep the block map and
     * bounds consistent.
     */
    @GameTest(template = TEMPLATE)
    public static void contraptionAddAndRemoveBlocks(GameTestHelper helper) {
        helper.setBlock(MOTOR_POS, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
                .setValue(ServoMotorBlock.FACING, Direction.EAST));
        ServoMotorBlockEntity motor = (ServoMotorBlockEntity) helper.getBlockEntity(MOTOR_POS);
        motor.initContraption();

        Contraption contraption = motor.getContraption();
        if (contraption == null) {
            helper.fail("Expected a contraption after placement");
            return;
        }

        // Use a local position far from anything else so the bounds effect is unambiguous.
        BlockPos far = new BlockPos(0, 10, 0); // block AABB spans y in [10, 11]
        int before = contraption.getBlocks().size();

        contraption.putBlock(far, Blocks.OAK_PLANKS.defaultBlockState(), null, null);
        if (contraption.getBlocks().size() != before + 1
                || !contraption.getBlocks().get(far).state().is(Blocks.OAK_PLANKS)) {
            helper.fail("putBlock should have added the plank at " + far);
            return;
        }
        if (contraption.getBounds().maxY < 11.0) {
            helper.fail("Bounds should have grown to include y=10; got " + contraption.getBounds());
            return;
        }

        if (contraption.removeBlock(far) == null) {
            helper.fail("removeBlock should have returned the removed plank");
            return;
        }
        if (contraption.getBlocks().size() != before || contraption.getBlocks().containsKey(far)) {
            helper.fail("removeBlock should have removed the plank at " + far);
            return;
        }
        // Bounds recomputed from scratch must no longer reach y=10.
        if (contraption.getBounds().maxY >= 11.0) {
            helper.fail("Bounds should have been recomputed to exclude y=10; got " + contraption.getBounds());
            return;
        }

        helper.succeed();
    }
}
