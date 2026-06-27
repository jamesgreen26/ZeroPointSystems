package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.ServoMotorBlock;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ServoMotorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos MOTOR_POS = new BlockPos(2, 2, 2);
    private static final BlockPos FRONT_POS = MOTOR_POS.east();

    @GameTest(template = TEMPLATE)
    public static void assembleRemovesStructure_disassembleRestoresIt(GameTestHelper helper) {
        helper.setBlock(MOTOR_POS, ModBlocks.SERVO_MOTOR.get().defaultBlockState()
                .setValue(ServoMotorBlock.FACING, Direction.EAST));
        helper.setBlock(FRONT_POS, Blocks.STONE);

        BlockEntity be = helper.getBlockEntity(MOTOR_POS);
        if (!(be instanceof ServoMotorBlockEntity motor)) {
            helper.fail("Expected a ServoMotorBlockEntity at " + MOTOR_POS + ", got " + be);
            return;
        }

        // Assemble: the block in front should be lifted out of the world into the contraption.
        motor.assemble();
        helper.assertBlockNotPresent(Blocks.STONE, FRONT_POS);
        if (!motor.isRunning() || motor.getContraption() == null) {
            helper.fail("Motor should be running with a contraption after assembly");
            return;
        }

        // Disassemble (at angle 0): the block should be written straight back to its origin.
        motor.disassemble();
        helper.assertBlockPresent(Blocks.STONE, FRONT_POS);
        if (motor.isRunning() || motor.getContraption() != null) {
            helper.fail("Motor should be idle with no contraption after disassembly");
            return;
        }

        helper.succeed();
    }
}
