package g_mungus.zps.block.datagen;

import java.util.ArrayList;
import java.util.List;

import static g_mungus.zps.block.datagen.BlockDataGenerator.FOLDER;
import static g_mungus.zps.block.datagen.BlockDataGenerator.blocksToDatagen;

public class BlockItemGenerator {
    public static void generate(String name) {
        String json = """
            {
              "parent": "zps:block/decor/%1$s"
            }
            """.formatted(name);
        String path = FOLDER + "assets/zps/models/item/" + name + ".json";

        FileWriter.writeFile(path, json);
    }

    public static void generateWallTag() {
        List<String> walls = new ArrayList<>();
        blocksToDatagen.forEach((name, types) -> {
            if (types.contains(BlockType.wall)) {
                walls.add("zps:" + name + "_wall");
            }
        });
        String path = FOLDER + "data/minecraft/tags/items/walls.json";
        FileWriter.writeFile(path, new TagJsonModel(walls, false).toJsonString());
    }

    public static void generateStairTag() {
        List<String> stairs = new ArrayList<>();
        blocksToDatagen.forEach((name, types) -> {
            if (types.contains(BlockType.stairs)) {
                stairs.add("zps:" + name + "_stairs");
            }
        });
        String path = FOLDER + "data/minecraft/tags/items/stairs.json";
        FileWriter.writeFile(path, new TagJsonModel(stairs, false).toJsonString());
    }

    public static void generateSlabTag() {
        List<String> slabs = new ArrayList<>();
        blocksToDatagen.forEach((name, types) -> {
            if (types.contains(BlockType.slab)) {
                slabs.add("zps:" + name + "_slab");
            }
        });
        String path = FOLDER + "data/minecraft/tags/items/slabs.json";
        FileWriter.writeFile(path, new TagJsonModel(slabs, false).toJsonString());
    }
}
