package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Covers {@code PowderSnowCauldronScoop}: sneak-use with empty hands takes a snowball per layer. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowderSnowCauldronGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos CAULDRON = new BlockPos(3, 2, 3);

    @GameTest(template = TEMPLATE)
    public static void sneakEmptyHand_takesOneLayerAsSnowball(GameTestHelper helper) {
        helper.setBlock(CAULDRON, powderSnowCauldron(3));
        Player player = helper.makeMockSurvivalPlayer();
        player.setShiftKeyDown(true);

        InteractionResult result = use(helper, player);

        helper.assertTrue(result.consumesAction(), "Scooping should consume the use, got " + result);
        helper.assertBlockPresent(Blocks.POWDER_SNOW_CAULDRON, CAULDRON);
        helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 2);
        helper.assertTrue(player.getMainHandItem().is(Items.SNOWBALL) && player.getMainHandItem().getCount() == 1,
                "Player should hold one snowball, got " + player.getMainHandItem());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sneakEmptyHand_lastLayerLeavesEmptyCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, powderSnowCauldron(1));
        Player player = helper.makeMockSurvivalPlayer();
        player.setShiftKeyDown(true);

        use(helper, player);

        helper.assertBlockPresent(Blocks.CAULDRON, CAULDRON);
        helper.assertTrue(player.getMainHandItem().is(Items.SNOWBALL), "Player should hold a snowball");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void plainEmptyHand_leavesCauldronAlone(GameTestHelper helper) {
        helper.setBlock(CAULDRON, powderSnowCauldron(3));
        Player player = helper.makeMockSurvivalPlayer();

        InteractionResult result = use(helper, player);

        helper.assertTrue(result == InteractionResult.PASS,
                "A plain click should fall through to the default interaction, got " + result);
        helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 3);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Player should still be empty-handed");
        helper.succeed();
    }

    /** Vanilla's bucket interaction on the same cauldron must be unaffected by the new air entry. */
    @GameTest(template = TEMPLATE)
    public static void bucket_stillFillsWithPowderSnow(GameTestHelper helper) {
        helper.setBlock(CAULDRON, powderSnowCauldron(3));
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));

        use(helper, player);

        helper.assertBlockPresent(Blocks.CAULDRON, CAULDRON);
        helper.assertTrue(player.getMainHandItem().is(Items.POWDER_SNOW_BUCKET), "Bucket should be filled");
        helper.succeed();
    }

    private static InteractionResult use(GameTestHelper helper, Player player) {
        BlockPos abs = helper.absolutePos(CAULDRON);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return helper.getBlockState(CAULDRON).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static BlockState powderSnowCauldron(int level) {
        return Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }
}
