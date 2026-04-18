package g_mungus.zps.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.compat.create.CreateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ZPSMod.MOD_ID)
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

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    public static boolean isSableLoaded() { return ModList.get().isLoaded("sable"); }

    public static BlockPos toWorldPos(ServerLevel level, BlockPos pos) {
        Position projectedPos = pos.getCenter();
        Vec3 worldPos = SableCompanion.INSTANCE.projectOutOfSubLevel(level, projectedPos);
        if (isVSLoaded()) {
            worldPos = VSCompat.shipToWorld(level, new BlockPos((int) worldPos.x, (int) worldPos.y, (int) worldPos.z));
        } else {
            return new BlockPos((int) worldPos.x, (int) worldPos.y, (int) worldPos.z);
        }
        return new BlockPos((int) worldPos.x, (int) worldPos.y, (int) worldPos.z);
    }

    @SubscribeEvent
    public static void onRegisterScriptCommandsEvent(RegisterScriptCommandsEvent event) {
        if (isSableLoaded()) {
            SableCompat.registerScriptCommands(event);
        }
        if (isVSLoaded()) {
            VSCompat.registerScriptCommands(event);
        }
        if (isCreateLoaded()) {
            CreateCompat.registerScriptCommands(event);
        }
    }

    public static void onModInit(IEventBus modEventBus) {
        if (isCreateLoaded()) {
            CreateCompat.init(modEventBus);
        }
    }
}
