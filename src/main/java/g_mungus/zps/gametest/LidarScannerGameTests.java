package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.LidarScannerBlock;
import g_mungus.zps.lidar.HeightMapRaycast;
import g_mungus.zps.lidar.LidarRaycasts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LidarScannerGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final double MAX_CAST_DISTANCE = 512.0;
    private static final double RAY_START_FACE_OFFSET = 0.5001;

    private static Vec3 rayStart(BlockPos scannerAbsPos, Direction facing) {
        return Vec3.atCenterOf(scannerAbsPos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(RAY_START_FACE_OFFSET));
    }

    private static double expectedDistanceToTargetFace(Vec3 start, BlockPos targetAbs, Direction facing) {
        return switch (facing) {
            case EAST -> targetAbs.getX() - start.x;
            case WEST -> start.x - (targetAbs.getX() + 1.0);
            case SOUTH -> targetAbs.getZ() - start.z;
            case NORTH -> start.z - (targetAbs.getZ() + 1.0);
            case UP -> targetAbs.getY() - start.y;
            case DOWN -> start.y - (targetAbs.getY() + 1.0);
        };
    }

    private static void runDirectionalRaycastTest(GameTestHelper helper, Direction facing) {
        BlockPos scannerRel = new BlockPos(3, 1, 3);
        BlockPos gapRel = scannerRel.relative(facing);
        BlockPos targetRel = scannerRel.relative(facing, 2);

        helper.setBlock(gapRel, Blocks.AIR);
        helper.setBlock(targetRel, Blocks.STONE);
        helper.setBlock(scannerRel, ModBlocks.LIDAR_SCANNER.get().defaultBlockState()
                .setValue(LidarScannerBlock.FACING, facing)
                .setValue(LidarScannerBlock.POWERED, false)
                .setValue(LidarScannerBlock.CONNECTED, false));

        BlockPos scannerAbs = helper.absolutePos(scannerRel);
        BlockPos targetAbs = helper.absolutePos(targetRel);
        Vec3 start = rayStart(scannerAbs, facing);
        Vec3 direction = Vec3.atLowerCornerOf(facing.getNormal());

        double measuredDistance = LidarRaycasts.raycast(helper.getLevel(), start, direction, MAX_CAST_DISTANCE);
        double measuredHeightMapDistance = HeightMapRaycast.INSTANCE.invoke(helper.getLevel(), start, direction, MAX_CAST_DISTANCE);
        double expectedDistance = expectedDistanceToTargetFace(start, targetAbs, facing);

        if (expectedDistance <= 0.0) {
            helper.fail("Lidar ray expected a positive air-gap distance but got expectedDistance="
                    + expectedDistance + ", facing=" + facing + ", scannerAbs=" + scannerAbs + ", targetAbs=" + targetAbs + ", start=" + start);
            return;
        }

        if (measuredDistance < 0.0) {
            helper.fail("Lidar raycast missed target block: measuredDistance=" + measuredDistance + ", facing=" + facing);
            return;
        }

        double tolerance = 1.0E-4;
        if (Math.abs(measuredDistance - expectedDistance) > tolerance) {
            helper.fail("Lidar raycast distance mismatch: measured=" + measuredDistance
                    + ", measuredHeightMap=" + measuredHeightMapDistance
                    + ", expected=" + expectedDistance
                    + ", facing=" + facing
                    + ", scannerAbs=" + scannerAbs
                    + ", targetAbs=" + targetAbs
                    + ", start=" + start);
            return;
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void downwardRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.DOWN);
    }

    @GameTest(template = TEMPLATE)
    public static void upwardRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.UP);
    }

    @GameTest(template = TEMPLATE)
    public static void northRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.NORTH);
    }

    @GameTest(template = TEMPLATE)
    public static void southRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.SOUTH);
    }

    @GameTest(template = TEMPLATE)
    public static void westRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.WEST);
    }

    @GameTest(template = TEMPLATE)
    public static void eastRaycast_withAirGap_matchesExpectedDistance(GameTestHelper helper) {
        runDirectionalRaycastTest(helper, Direction.EAST);
    }
}
