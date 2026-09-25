package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A block that requires the correct tool drops nothing unless some tool is tagged as correct for
 * it, so every such ZPS block must be in a mineable tag.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class MineableTagGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void everyBlockNeedingAToolHasOne(GameTestHelper helper) {
        List<ResourceLocation> untagged = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(ZPSMod.MOD_ID)) continue;
            BlockState state = block.defaultBlockState();
            if (!state.requiresCorrectToolForDrops()) continue;
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                    || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE)) {
                continue;
            }
            untagged.add(id);
        }
        helper.assertTrue(untagged.isEmpty(), "Blocks that need a tool but no tool mines: " + untagged);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gasBlocksDropForAPickaxe(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (String name : List.of("gas_duct", "vent", "creative_gas_generator")) {
            Block block = BuiltInRegistries.BLOCK.get(ZPSMod.resource(name));
            helper.assertTrue(pickaxe.isCorrectToolForDrops(block.defaultBlockState()),
                    "An iron pickaxe should be the correct tool for " + name);
        }
        helper.succeed();
    }
}
