package g_mungus.zps.compat;

import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.compat.create.CreateCompat;
import g_mungus.zps.compat.genesis.GenesisCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import shipwrights.genesis.space.Celestial;

@Mod.EventBusSubscriber
public class Compat {

    public static final String ZPL_MOD_ID = "zpl";

    public static final ResourceKey<Registry<Celestial>> CELESTIALS_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("genesis", "celestials"));

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

    static boolean isVSLoaded() {
        return ModList.get().isLoaded("valkyrienskies");
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    public static boolean isGenesisLoaded() {return ModList.get().isLoaded("genesis");}

    public static BlockPos toWorldPos(ServerLevel level, BlockPos pos) {
        if (isVSLoaded()) {
            Vec3 truePos = VSCompat.shipToWorld(level, pos);
            return new BlockPos((int) truePos.x, (int) truePos.y, (int) truePos.z);
        } else {
            return pos;
        }
    }

    public static Vec3 toWorldPos(ServerLevel level, Vec3 pos) {
        if (isVSLoaded()) {
            return VSCompat.shipToWorld(level, pos);
        } else {
            return pos;
        }
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the
    /// grid managing anchorPos. Identity when VS is not loaded.
    public static Vec3 toLocalSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        if (isVSLoaded()) {
            Vec3 worldPos = VSCompat.shipToWorld(level, pos);
            return VSCompat.worldToShip(level, anchorPos, worldPos);
        } else {
            return pos;
        }
    }

    @SubscribeEvent
    public static void onRegisterScriptCommandsEvent(RegisterScriptCommandsEvent event) {
        if (isVSLoaded()) {
            VSCompat.registerScriptCommands(event);
        }
        if (isCreateLoaded()) {
            CreateCompat.registerScriptCommands(event);
        }
        if (isGenesisLoaded()) {
            GenesisCompat.registerScriptCommands(event);
        }
    }

    public static void onModInit(IEventBus modEventBus) {
        if (isCreateLoaded()) {
            CreateCompat.init(modEventBus);
        }
    }
}
