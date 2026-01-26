package g_mungus.zps.block.datagen;

public class PillarGenerator {
    public static void generateSlab(String name) {
        generateBlockState(name);
        generateBlockModel(name);
        generateLootTable(name);
        BlockItemGenerator.generate(name + "_pillar");
    }

    private static void generateBlockModel(String name) {
        generatePillarModel(name);
    }

    private static void generatePillarModel(String name) {
        String json = """
                {
                "parent": "minecraft:block/cube_column",
                "textures": {
                "end": "minecraft:block/%1$s_top",
                "side": "minecraft:block/%1$s"
                }
                }
              """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "assets/zps/models/block/decor/" + name + "_pillar.json";

        FileWriter.writeFile(path, json);
    }

    private static void generateBlockState(String name) {
        String json = """
                {
                  "variants": {
                    "axis=x": {
                      "model": "zps:block/decor/%1$s",
                        "y": 90,
                        "x": 90
                    },
                    "axis=y": {
                      "model": "zps:block/decor/%1$s"
                    },
                    "axis=z": {
                      "model": "zps:block/decor/%1$s",
                        "x": 90
                    }
                  }
                }
              """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "assets/zps/blockstates/" + name + "_pillar.json";

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
                                  "name": "zps:%1$s_pillar"
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
        String path = BlockDataGenerator.FOLDER + "data/zps/loot_tables/blocks/" + name + "_pillar.json";

        FileWriter.writeFile(path, json);
    }
}
