package g_mungus.zps.compat;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.compat.create.CreateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
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

    public static boolean isVSLoaded() {
        return ModList.get().isLoaded("valkyrienskies");
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    public static boolean isSableLoaded() { return ModList.get().isLoaded("sable"); }

    public static BlockPos toWorldPos(ServerLevel level, BlockPos pos) {
        if (isVSLoaded()) {
            Vec3 truePos = VSCompat.shipToWorld(level, pos);
            return new BlockPos((int) truePos.x, (int) truePos.y, (int) truePos.z);
        }
        if (isSableLoaded()) {
            Vec3 worldPos = SableCompat.subLevelToWorld(level, pos);
            return new BlockPos((int) worldPos.x, (int) worldPos.y, (int) worldPos.z);
        }
        return pos;
    }

    public static Vec3 toWorldPos(ServerLevel level, Vec3 pos) {
        if (isVSLoaded()) {
            return VSCompat.shipToWorld(level, pos);
        }
        if (isSableLoaded()) {
            return SableCompat.subLevelToWorld(level, pos);
        }
        return pos;
    }

    /// Projects pos (in the local grid space of the grid managing anchorPos) into world space.
    /// anchorPos resolves which ship/sublevel to use, so positions outside that grid's strict
    /// bounds still transform correctly. Identity when neither VS nor Sable is loaded.
    public static Vec3 toWorldPos(ServerLevel level, BlockPos anchorPos, Vec3 pos) {
        if (isVSLoaded()) {
            return VSCompat.shipToWorld(level, anchorPos, pos);
        }
        if (isSableLoaded()) {
            return SableCompat.subLevelToWorld(level, anchorPos, pos);
        }
        return pos;
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the grid managing
    /// anchorPos. Identity when neither VS nor Sable is loaded.
    public static Vec3 toLocalSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        if (isVSLoaded()) {
            Vec3 worldPos = VSCompat.shipToWorld(level, pos);
            return VSCompat.worldToShip(level, anchorPos, worldPos);
        }
        if (isSableLoaded()) {
            Vec3 worldPos = SableCompat.subLevelToWorld(level, pos);
            return SableCompat.worldToSubLevel(level, anchorPos, worldPos);
        }
        return pos;
    }

    @SubscribeEvent
    public static void onRegisterScriptCommandsEvent(RegisterScriptCommandsEvent event) {
        if (isVSLoaded()) {
            VSCompat.registerScriptCommands(event);
        } else if (isSableLoaded()) {
            SableCompat.registerScriptCommands(event);
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
