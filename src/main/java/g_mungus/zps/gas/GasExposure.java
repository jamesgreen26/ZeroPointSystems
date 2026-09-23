package g_mungus.zps.gas;

import g_mungus.zps.config.ZPSConfig;
import net.minecraft.world.entity.Entity;

/**
 * What gas at a given temperature does to an entity standing in it.
 *
 * <p>Hot gas sets the entity alight and hurts it as fire would; cold gas freezes it as powder snow
 * would, frost creeping over the screen until, fully frozen, it takes freezing damage. Anything
 * in between does nothing. The thresholds are the {@code EntityBurnGasTemperature} and
 * {@code EntityFreezeGasTemperature} config values, and no temperature at all —
 * {@link Double#NaN}, for gas that is not there — does nothing either.
 *
 * <p>Meant to be called once per server tick for as long as the entity is in the gas; every
 * figure here is a per-tick one, and each effect wears off by itself once the calls stop.
 */
public final class GasExposure {

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
     * Apply one tick of gas at {@code temperatureK} to {@code entity}. Server side.
     *
     * @return whether the gas did anything: it was hot or cold enough, and the entity was not
     *         immune to what it does
     */
    public static boolean expose(Entity entity, double temperatureK) {
        if (isBurning(temperatureK)) {
            return burn(entity);
        }
        if (isFreezing(temperatureK)) {
            return freeze(entity);
        }
        return false;
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
}
