package g_mungus.zps.block.datagen;

public class BlockItemGenerator {
    public static void generate(String name) {
        String json = """
            {
              "parent": "zps:block/decor/%1$s"
            }
            """.formatted(name);
        String path = BlockDataGenerator.FOLDER + "assets/zps/models/item/" + name + ".json";

        FileWriter.writeFile(path, json);
    }
}
