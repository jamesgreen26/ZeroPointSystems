package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.gas.api.GasEnvironment;
import g_mungus.zps.gas.api.GasEnvironmentEvent;
import g_mungus.zps.gas.api.GasEnvironments;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.world.AtmosphericComposition;
import g_mungus.zps.world.BiomeTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * Every living entity is always in some gas: each server tick, from the start of its base tick
 * ({@code LivingEntityMixin}), it gets {@link GasExposure} to its {@link GasEnvironment}. That covers
 * players and anything riding, which the level's entity tick events skip.
 *
 * <p>The environment starts as the atmosphere at its eyes — a sealed room's gas if its eyes are in
 * one ({@link RoomManager}), otherwise the biome's. Outside a room the temperature is the biome's, in kelvin
 * ({@link BiomeTemperature}); where the biome's {@link AtmosphericComposition} holds no registered gas
 * there is nothing to carry heat or cold, except {@code zps:vacuum}, which is no Kelvin gas but still
 * exposes an entity to the biome's temperature. Then queued modifiers ({@link GasEnvironments}) and
 * {@link GasEnvironmentEvent} listeners change it — a reactor's chamber, a vent's gas — and what is
 * left is applied once.
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class AtmosphereExposure {

    private AtmosphereExposure() {
    }

    /** One tick of gas for {@code entity}, at the start of its base tick. Server side. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide() || !entity.isAlive()) {
            return;
        }
        GasEnvironment environment = atmosphere(entity);
        GasEnvironments.applyQueued(environment);
        NeoForge.EVENT_BUS.post(new GasEnvironmentEvent(entity, environment));
        if (environment.isExposed()) {
            GasExposure.expose(entity, environment.temperatureK(), !environment.isBreathable());
        }
        if (entity instanceof Player) {
            ((AtmosphereBreather) entity).zps$setGasConditions(GasConditions.of(environment));
        }
    }

    /**
     * The atmosphere at {@code entity}'s eyes, before anything else has changed it: the sealed room
     * its eyes are in, if any — on a sublevel it is touching before the world's, see
     * {@link RoomManager#roomAround} — otherwise the biome's where it is in the world.
     */
    public static GasEnvironment atmosphere(LivingEntity entity) {
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        if (entity.level() instanceof ServerLevel level) {
            Room room = RoomManager.get(level).roomAround(entity);
            if (room != null) {
                return new GasEnvironment(entity, eyes, room.temperatureK(), room.gases());
            }
        }
        BlockPos air = inWorld(entity.level(), eyes);
        Biome biome = entity.level().getBiome(air).value();
        AtmosphericComposition composition = AtmosphericComposition.of(biome);
        return new GasEnvironment(entity, eyes, temperatureK(biome, composition, air), composition.at(air.getY()));
    }

    /**
     * Where the atmosphere at {@code pos} is to be read. A position on a moving grid — a Sable
     * sublevel or a ship, whose blocks live in a plot far from where they appear — is where the grid
     * puts it in the world now; anything else is where it is. The outside air a ship flies through
     * is the air where it flies, not the air at its plot.
     */
    public static BlockPos inWorld(Level level, BlockPos pos) {
        GridSpace grid = Compat.gridOf(level, pos);
        return grid == null ? pos : BlockPos.containing(grid.toWorld(Vec3.atCenterOf(pos)));
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        GasExposure.onBreathe(event);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GasExposure.onChangedDimension(player);
        }
    }

    /**
     * The temperature of the atmosphere at {@code pos}, in kelvin, or {@link Double#NaN} where there
     * is nothing exposable. A position on a moving grid is read where the grid is ({@link #inWorld}).
     */
    public static double temperatureK(Level level, BlockPos pos) {
        pos = inWorld(level, pos);
        Biome biome = level.getBiome(pos).value();
        return temperatureK(biome, AtmosphericComposition.of(biome), pos);
    }

    /** As {@link #temperatureK(Level, BlockPos)}, for {@code biome} with {@code composition} as its air. */
    public static double temperatureK(Biome biome, AtmosphericComposition composition, BlockPos pos) {
        if (!isExposable(composition, pos.getY())) {
            return Double.NaN;
        }
        return BiomeTemperature.kelvinAt(biome, pos);
    }

    /**
     * Whether an entity can breathe {@code composition} at height {@code y}: enough of it is
     * {@link ModGasTags#BREATHABLE} ({@link ModGasTags#MIN_BREATHABLE_SHARE}).
     */
    public static boolean isBreathable(Level level, AtmosphericComposition composition, int y) {
        return ModGasTags.isBreathable(level, composition.at(y));
    }

    private static boolean isExposable(AtmosphericComposition composition, int y) {
        for (ResourceLocation gas : composition.at(y).keySet()) {
            if (gas.equals(AtmosphericComposition.VACUUM) || GasTypeRegistry.INSTANCE.getGasType(gas) != null) {
                return true;
            }
        }
        return false;
    }
}
