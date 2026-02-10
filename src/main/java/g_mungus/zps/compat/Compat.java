package g_mungus.zps.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

public class Compat {

    public static final String ZPL_MOD_ID = "zpl";

    public static boolean isCreateDeployer(Player player) {
        ComponentContents contents = player.getDisplayName().getContents();
        if (contents instanceof TranslatableContents translatableContents) {
            return translatableContents.getKey().equals("create.block.deployer.damage_source_name");
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public static boolean isCreateWrench(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).equals(ResourceLocation.fromNamespaceAndPath("create", "wrench"));
    }

    private static boolean isVSLoaded() {
        return ModList.get().isLoaded("valkyrienskies");
    }

    public static BlockPos toWorldPos(ServerLevel level, BlockPos pos) {
        if (isVSLoaded()) {
            Vec3 truePos = VSCompat.shipToWorld(level, pos);
            return new BlockPos((int) truePos.x, (int) truePos.y, (int) truePos.z);
        } else {
            return pos;
        }
    }
}
