package g_mungus.zps.gas;

import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.networking.DrowningS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectUtil;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What gas at a given temperature does to an entity standing in it.
 *
 * <p>Hot gas sets the entity alight and hurts it as fire would, and gas past
 * {@link #LAVA_TEMPERATURE_K} as lava would; cold gas freezes it as powder snow
 * would, frost creeping over the screen until, fully frozen, it takes freezing damage. Anything
 * in between does nothing. The thresholds are the {@code EntityBurnGasTemperature} and
 * {@code EntityFreezeGasTemperature} config values, and no temperature at all —
 * {@link Double#NaN}, for gas that is not there — does nothing either.
 *
 * <p>Gas that cannot be breathed drowns the entity as water would: its air runs down at vanilla's
 * pace, and once out it takes drowning damage, with the same exemptions water gives (water
 * breathing, mobs that breathe underwater, invulnerable players).
 *
 * <p>Drowning is decided on the server only. Air runs on both sides, though, so a client left to
 * itself would refill what the server drains and the bubbles would flicker: a player's client is
 * told whenever its drowning starts or stops ({@link DrowningS2CPacket}) and drowns along with it.
 *
 * <p>Meant to be called once per tick for as long as the entity is in the gas, before the entity's
 * own tick; every figure here is a per-tick one, and each effect wears off by itself once the calls
 * stop. Server side.
 */
public final class GasExposure {

    /** Less than this of a gas, in kilograms (0.01 g), is a trace an entity is not exposed to. */
    public static final double MIN_GAS_KG = 1.0e-5;
    /** Gas hotter than this, in kelvin, hurts an entity as lava does rather than as fire. */
    public static final double LAVA_TEMPERATURE_K = 5000.0;
    /** How long an entity keeps burning after the gas that lit it has passed, in seconds. */
    public static final float BURN_SECONDS = 4.0f;
    /** Damage dealt each tick hot gas reaches an entity; vanilla's standing-in-fire figure. */
    public static final float BURN_DAMAGE = 1.0f;
    /** How fast powder snow freezes an entity, in frozen ticks per tick. Vanilla's figure. */
    private static final int FREEZE_TICKS_PER_TICK = 1;
    /** How fast an entity thaws when not in powder snow, in frozen ticks per tick. Vanilla's figure. */
    private static final int THAW_TICKS_PER_TICK = 2;

    private GasExposure() {
    }

    /** Whether gas at {@code temperatureK} does anything to an entity at all. */
    public static boolean isHarmful(double temperatureK) {
        return isBurning(temperatureK) || isFreezing(temperatureK);
    }

    public static boolean isBurning(double temperatureK) {
        return temperatureK >= ZPSConfig.entityBurnGasTemperatureK();
    }

    public static boolean isFreezing(double temperatureK) {
        return temperatureK <= ZPSConfig.entityFreezeGasTemperatureK();
    }

    /**
     * Apply one tick of gas at {@code temperatureK} to {@code entity}, which is {@code drowning} if
     * the gas cannot be breathed.
     *
     * @return whether the gas's temperature did anything: it was hot or cold enough, and the entity
     *         was not immune to what it does
     */
    public static boolean expose(Entity entity, double temperatureK, boolean drowning) {
        if (drowning && entity instanceof AtmosphereBreather breather) {
            breather.zps$setDrowningTick(entity.level().getGameTime());
        }
        if (temperatureK > LAVA_TEMPERATURE_K) {
            return lava(entity);
        }
        if (isBurning(temperatureK)) {
            return burn(entity);
        }
        if (isFreezing(temperatureK)) {
            return freeze(entity);
        }
        return false;
    }

    /** One tick of searing gas: vanilla's lava, with its fire, its damage and its immunities. */
    public static boolean lava(Entity entity) {
        if (entity.fireImmune()) {
            return false;
        }
        entity.lavaHurt();
        return true;
    }

    /** One tick of hot gas: alight, and hurt as by fire. */
    public static boolean burn(Entity entity) {
        if (entity.fireImmune()) {
            return false;
        }
        entity.igniteForSeconds(BURN_SECONDS);
        entity.hurt(entity.level().damageSources().inFire(), BURN_DAMAGE);
        return true;
    }

    /**
     * One tick of cold gas: frost, at powder snow's pace.
     *
     * <p>Powder snow freezes an entity a tick per tick, and out of it they thaw two a tick. That
     * thaw runs in the entity's own tick, which for anything applied to it from outside comes
     * after this, so three go on here for a net one: the same creep of frost as standing in the
     * snow. The count is let run two past full for the same reason: vanilla checks for a fully
     * frozen entity only after thawing it, and a count stopped exactly at full would be two short
     * by then and never deal its damage. Two past, it reads as full at that check, and vanilla
     * deals freezing damage exactly as it does in the snow.
     */
    public static boolean freeze(Entity entity) {
        if (!entity.canFreeze()) {
            return false;
        }
        int full = entity.getTicksRequiredToFreeze() + THAW_TICKS_PER_TICK;
        entity.setTicksFrozen(Math.min(full,
                entity.getTicksFrozen() + FREEZE_TICKS_PER_TICK + THAW_TICKS_PER_TICK));
        return true;
    }

    /**
     * Whether {@link #expose} has said {@code entity} is drowning this tick. The tick before counts
     * too: a reactor exposes what is inside it from the level tick's start, a game-time step ahead
     * of the entity's own tick.
     */
    public static boolean isDrowning(LivingEntity entity) {
        if (!(entity instanceof AtmosphereBreather breather)) {
            return false;
        }
        // Never marked is Long.MIN_VALUE, which this subtraction wraps to a negative.
        long since = entity.level().getGameTime() - breather.zps$getDrowningTick();
        return since >= 0 && since <= 1;
    }

    /**
     * Drowning in gas goes through NeoForge's own breathing, which runs in the entity's base tick just
     * after {@code LivingEntityMixin} has exposed it: the air in its eyes is not breathable, unless
     * something would let it breathe underwater. On the client, a player drowns when the server has
     * said so.
     */
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        boolean drowning;
        if (entity.level().isClientSide()) {
            drowning = entity instanceof AtmosphereBreather breather && breather.zps$isSyncedDrowning();
        } else {
            drowning = isDrowning(entity);
            if (entity instanceof ServerPlayer player) {
                syncDrowning(player, drowning);
            }
        }
        if (!event.canBreathe() || !drowning) {
            return;
        }
        if (!entity.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value()) || MobEffectUtil.hasWaterBreathing(entity)
                || (entity instanceof Player player && player.getAbilities().invulnerable)) {
            return;
        }
        event.setCanBreathe(false);
    }

    /** Tell {@code player}'s client if its drowning has started or stopped since it was last told. */
    private static void syncDrowning(ServerPlayer player, boolean drowning) {
        AtmosphereBreather breather = (AtmosphereBreather) player;
        if (breather.zps$isSyncedDrowning() != drowning) {
            breather.zps$setSyncedDrowning(drowning);
            // A fake player has no client to tell, and its stand-in connection cannot be asked.
            if (!(player instanceof FakePlayer) && player.connection.hasChannel(DrowningS2CPacket.TYPE)) {
                PacketDistributor.sendToPlayer(player, new DrowningS2CPacket(drowning));
            }
        }
    }

    /**
     * A player keeps its server entity through a change of dimension but gets a new one on the client,
     * which starts out breathing: forget what was sent, so a player still drowning is told again.
     */
    public static void onChangedDimension(ServerPlayer player) {
        ((AtmosphereBreather) player).zps$setSyncedDrowning(false);
    }
}
