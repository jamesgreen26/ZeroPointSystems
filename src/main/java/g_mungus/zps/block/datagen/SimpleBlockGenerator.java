package g_mungus.zps.block.datagen;

public class SimpleBlockGenerator {
    public static void generateSimpleBlock(String name) {
        generateBlockState(name);
        generateBlockModel(name);
        generateLootTable(name);
        BlockItemGenerator.generate(name);
    }

    private static void generateBlockModel(String name) {
        generateModel(name);
    }

    private static void generateModel(String name) {
        String json = """
              {
                "parent": "minecraft:block/cube_all",
                "textures": {
                  "all": "zps:block/decor/%1$s"
                }
              }
              """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "assets/zps/models/block/decor/" + name + ".json";

        FileWriter.writeFile(path, json);
    }

    private static void generateBlockState(String name) {
        String json = """
              {
                "variants": {
                  "": {
                    "model": "zps:block/decor/%1$s"
                  }
                }
              }
              """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "assets/zps/blockstates/" + name + ".json";

        FileWriter.writeFile(path, json);
    }

    private static void generateLootTable(String name) {
        String json = """
                        {
                          "type": "minecraft:block",
                          "pools": [
                            {
                              "bonus_rolls": 0.0,
                              "rolls": 1.0,
                              "entries": [
                                {
                                  "type": "minecraft:item",
                                  "name": "zps:%1$s"
                                }
                              ],
                              "conditions": [
                                {
                                  "condition": "minecraft:survives_explosion"
                                }
                              ]
                            }
                          ]
                        }
              """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "data/zps/loot_tables/blocks/" + name + ".json";

        FileWriter.writeFile(path, json);
    }
}
