package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Light reads right however high up: the open sky above the world is fully lit, past y 2047 too, where the
 * light engine's packed positions run out of height and used to wrap round to the dark below the world.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LightAtHeightGameTests {

    @GameTest(template = "gametest/flat_7x4x7")
    public static void skyIsLitAboveThePackableHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(3, 2, 3));
        for (int y : new int[]{1000, 2047, 2048, 3000, 100_000}) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            int sky = level.getBrightness(LightLayer.SKY, pos);
            helper.assertTrue(sky == 15, "The open sky at y " + y + " should be fully lit, reads " + sky);
            int block = level.getBrightness(LightLayer.BLOCK, pos);
            helper.assertTrue(block == 0, "Nothing up at y " + y + " gives off light, reads " + block);
            int raw = level.getRawBrightness(pos, 0);
            helper.assertTrue(raw == 15, "The raw brightness at y " + y + " should be full, reads " + raw);
        }
        helper.succeed();
    }
}
