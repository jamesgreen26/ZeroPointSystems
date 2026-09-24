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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import shipwrights.genesis.space.Celestial;

import java.util.List;

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

    public static boolean isVSLoaded() {
        return ModList.get().isLoaded("valkyrienskies");
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    public static boolean isGenesisLoaded() {return ModList.get().isLoaded("genesis");}

    public static boolean isClockworkLoaded() { return ModList.get().isLoaded("vs_clockwork"); }

    public static BlockPos toWorldPos(ServerLevel level, BlockPos pos) {
        if (isVSLoaded()) {
            Vec3 truePos = VSCompat.shipToWorld(level, pos);
            return new BlockPos((int) truePos.x, (int) truePos.y, (int) truePos.z);
        }
        return pos;
    }

    public static Vec3 toWorldPos(Level level, Vec3 pos) {
        if (isVSLoaded()) {
            return VSCompat.shipToWorld(level, pos);
        }
        return pos;
    }

    /// Projects pos (in the local grid space of the ship managing anchorPos) into world space.
    /// anchorPos resolves which ship to use, so positions outside that ship's strict bounds still
    /// transform correctly. Identity when VS is not loaded.
    public static Vec3 toWorldPos(Level level, BlockPos anchorPos, Vec3 pos) {
        if (isVSLoaded()) {
            return VSCompat.shipToWorld(level, anchorPos, pos);
        }
        return pos;
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the ship managing
    /// anchorPos. Identity when VS is not loaded.
    public static Vec3 toLocalSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        if (isVSLoaded()) {
            Vec3 worldPos = VSCompat.shipToWorld(level, pos);
            return VSCompat.worldToShip(level, anchorPos, worldPos);
        }
        return pos;
    }

    /// True when pos lies in the region VS keeps for its ships (the shipyard) but no ship owns it
    /// any more: a ship has gone, and whatever is still at pos is a leftover with no place in the
    /// world. False in the world proper, on a live ship, or when VS is not present.
    public static boolean isOrphanedGridPos(Level level, BlockPos pos) {
        if (isVSLoaded()) {
            return VSCompat.isOrphanedShipyardPos(level, pos);
        }
        return false;
    }

    /// The moving grid (VS ship) that pos belongs to, or null when pos is in the world proper or VS
    /// is not present.
    public static @Nullable GridSpace gridOf(Level level, BlockPos pos) {
        if (isVSLoaded()) {
            return VSCompat.gridOf(level, pos);
        }
        return null;
    }

    /// Every moving grid that reaches into worldBounds. Empty when VS is not present.
    public static List<GridSpace> gridsTouching(Level level, AABB worldBounds) {
        if (isVSLoaded()) {
            return VSCompat.gridsTouching(level, worldBounds);
        }
        return List.of();
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

    /** Clockwork's Steam when it is present, or the same gas defined here so ZPS can run without it. */
    public static GasType getOrCreateSteamGas() {
        if (isClockworkLoaded()) {
            return ClockworkCompat.getSteamGas();
        } else {
            return new GasType(
                    "Steam",
                    ResourceLocation.fromNamespaceAndPath("vs_clockwork", "steam"),
                    0.762,
                    1.223e-5,
                    2.2,
                    0.031,
                    111.0,
                    1.4,
                    ResourceLocation.fromNamespaceAndPath("kelvin", "textures/icons/steam.png")
            );
        }
    }

    public static GasType getOrCreateAetherGas() {
        if (isClockworkLoaded()) {
            return ClockworkCompat.getAetherGas();
        } else {
            return new GasType(
                    "Aether",
                    ResourceLocation.fromNamespaceAndPath("vs_clockwork", "aether"),
                    0.166,
                    1.96e-5,
                    5.1832,
                    0.151,
                    79.4,
                    1.66,
                    ResourceLocation.fromNamespaceAndPath("kelvin", "textures/icons/helium.png")
            );
        }
    }
}
