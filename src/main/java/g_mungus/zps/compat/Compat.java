package g_mungus.zps.compat;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.compat.computercraft.ComputerCraftCompat;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.valkyrienskies.kelvin.api.GasType;

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

    public static boolean isComputerCraftLoaded() { return ModList.get().isLoaded("computercraft"); }

    public static boolean isClockworkLoaded() { return ModList.get().isLoaded("vs_clockwork"); }

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

    public static Vec3 toWorldPos(Level level, Vec3 pos) {
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
    public static Vec3 toWorldPos(Level level, BlockPos anchorPos, Vec3 pos) {
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

    /// True when pos lies in the region a grid mod keeps for its grids (the VS shipyard, Sable's
    /// plots) but no grid owns it any more: a ship or sublevel has gone, and whatever is still at
    /// pos is a leftover with no place in the world. False in the world proper, on a live grid,
    /// or when no grid mod is present.
    public static boolean isOrphanedGridPos(Level level, BlockPos pos) {
        if (isVSLoaded()) {
            return VSCompat.isOrphanedShipyardPos(level, pos);
        }
        if (isSableLoaded()) {
            return SableCompat.isOrphanedPlotPos(level, pos);
        }
        return false;
    }

    /// The moving grid (VS ship or Sable sublevel) that pos belongs to, or null when pos is in the
    /// world proper or no grid mod is present.
    public static @Nullable GridSpace gridOf(Level level, BlockPos pos) {
        if (isVSLoaded()) {
            return VSCompat.gridOf(level, pos);
        }
        if (isSableLoaded()) {
            return SableCompat.gridOf(level, pos);
        }
        return null;
    }

    /// Every moving grid that reaches into worldBounds. Empty when no grid mod is present.
    public static List<GridSpace> gridsTouching(Level level, AABB worldBounds) {
        if (isVSLoaded()) {
            return VSCompat.gridsTouching(level, worldBounds);
        }
        if (isSableLoaded()) {
            return SableCompat.gridsTouching(level, worldBounds);
        }
        return List.of();
    }

    @SubscribeEvent
    public static void onRegisterScriptTypesEvent(RegisterScriptTypesEvent event) {
        if (isVSLoaded()) {
            VSCompat.registerScriptTypes(event);
        } else if (isSableLoaded()) {
            SableCompat.registerScriptTypes(event);
        }
    }

    /** After ZPS's own, so compat that names getters at runtime can see which names are taken. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRegisterScriptCommandsEvent(RegisterScriptCommandsEvent event) {
        if (isVSLoaded()) {
            VSCompat.registerScriptCommands(event);
        } else if (isSableLoaded()) {
            SableCompat.registerScriptCommands(event);
        }
        if (isCreateLoaded()) {
            CreateCompat.registerScriptCommands(event);
        }
        if (isComputerCraftLoaded()) {
            // Computer APIs say nothing about where they apply; what is known about them is given here.
            Map<String, TargetApplicability> apiApplicability = new HashMap<>();
            if (isSableLoaded()) {
                apiApplicability.put("sublevel", SableCompat.OnSubLevel.INSTANCE); // CC: Sable
                apiApplicability.put("aero", SableCompat.OnSubLevel.INSTANCE);
            }
            ComputerCraftCompat.registerScriptCommands(event, apiApplicability);
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
