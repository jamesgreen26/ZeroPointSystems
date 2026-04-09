package g_mungus.zps.commands.content.executors;

import g_mungus.zps.ZPSMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class DimensionIndexCommand {
    public static final ResourceLocation DATA_LOCATION = ZPSMod.resource("dimension_index");

    public static int getIndex(MinecraftServer server, ResourceLocation dim) {
        CompoundTag root = server.getCommandStorage().get(DATA_LOCATION);

        if (root.isEmpty()) {
            root.putInt("minecraft:overworld", 0);
            root.putInt("minecraft:the_nether", 1);
            root.putInt("minecraft:the_end", 2);
            root.putInt("__next", 3);
            server.getCommandStorage().set(DATA_LOCATION, root);
        }

        String key = dim.toString();
        if (root.contains(key)) {
            return root.getInt(key);
        }

        int next = root.getInt("__next");
        root.putInt(key, next);
        root.putInt("__next", next + 1);
        server.getCommandStorage().set(DATA_LOCATION, root);
        return next;
    }
}
