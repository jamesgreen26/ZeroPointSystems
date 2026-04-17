package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnumPropertyWithAliasesGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private EnumPropertyWithAliasesGameTests() {
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static CompoundTag cableState(String insulated) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "zps:cable");

        CompoundTag properties = new CompoundTag();
        properties.putString("north", "false");
        properties.putString("south", "false");
        properties.putString("east", "false");
        properties.putString("west", "false");
        properties.putString("up", "false");
        properties.putString("down", "false");
        properties.putString("insulated", insulated);
        state.put("Properties", properties);
        return state;
    }

    private static CompoundTag blockEntry(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        return block;
    }

    private static StructureTemplate createAliasTemplate(ServerLevel level) {
        CompoundTag root = new CompoundTag();
        root.put("size", ints(3, 1, 1));
        root.put("entities", new ListTag());

        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0, 0, 0, 0));
        blocks.add(blockEntry(2, 0, 0, 1));
        root.put("blocks", blocks);

        ListTag palette = new ListTag();
        palette.add(cableState("false"));
        palette.add(cableState("true"));
        root.put("palette", palette);

        HolderLookup.RegistryLookup<Block> blocksLookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        StructureTemplate template = new StructureTemplate();
        template.load(blocksLookup, root);
        return template;
    }

    private static void placeAliasTemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = createAliasTemplate(level);
        BlockPos placeAt = helper.absolutePos(new BlockPos(2, 1, 3));
        template.placeInWorld(level, placeAt, placeAt, new StructurePlaceSettings(), level.getRandom(), 2);
    }

    @GameTest(template = TEMPLATE)
    public static void cableTemplate_aliasBlockstatesDeserializeToExpectedEnums(GameTestHelper helper) {
        placeAliasTemplate(helper);

        helper.assertBlock(new BlockPos(2, 1, 3), block -> block == ModBlocks.CABLE.get(), () -> "Expected a cable at 2,1,3");
        helper.assertBlock(new BlockPos(4, 1, 3), block -> block == ModBlocks.CABLE.get(), () -> "Expected a cable at 4,1,3");
        helper.assertBlockProperty(new BlockPos(2, 1, 3), CableBlock.INSULATION_TYPE, InsulationType.NONE);
        helper.assertBlockProperty(new BlockPos(4, 1, 3), CableBlock.INSULATION_TYPE, InsulationType.INSULATION);
        helper.succeed();
    }
}
