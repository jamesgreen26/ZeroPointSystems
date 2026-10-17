package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
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
        MinecraftForge.EVENT_BUS.post(new GasEnvironmentEvent(entity, environment));
        if (environment.isExposed()) {
            GasExposure.expose(entity, environment.temperatureK(), !environment.isBreathable());
        }
        if (entity instanceof Player) {
            ((AtmosphereBreather) entity).zps$setGasConditions(GasConditions.of(environment));
        }
    }

    /**
     * The atmosphere at {@code entity}'s eyes, before anything else has changed it: the sealed room
     * its eyes are in, if any, otherwise the biome's.
     */
    public static GasEnvironment atmosphere(LivingEntity entity) {
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        if (entity.level() instanceof ServerLevel level) {
            Room room = RoomManager.get(level).roomAt(eyes);
            if (room != null) {
                return new GasEnvironment(entity, eyes, room.temperatureK(), room.gases());
            }
        }
        Biome biome = entity.level().getBiome(eyes).value();
        AtmosphericComposition composition = AtmosphericComposition.of(biome);
        return new GasEnvironment(entity, eyes, temperatureK(biome, composition, eyes), composition.at(eyes.getY()));
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

    /** The temperature of the atmosphere at {@code pos}, in kelvin, or {@link Double#NaN} where there is nothing exposable. */
    public static double temperatureK(Level level, BlockPos pos) {
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

    /** Whether any gas in {@code composition} at height {@code y} is {@link ModGasTags#BREATHABLE}. */
    public static boolean isBreathable(Level level, AtmosphericComposition composition, int y) {
        for (ResourceLocation gas : composition.at(y).keySet()) {
            if (ModGasTags.isBreathable(level, gas)) {
                return true;
            }
        }
        return false;
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
