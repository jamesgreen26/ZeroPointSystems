package g_mungus.zps.reactor;

/**
 * Fixed numbers the fusion reactor runs on. The handful of values a pack maker is expected to
 * tune (ignition and melt temperature, exchanger temperature, burst pressure, size limit) live in
 * {@link g_mungus.zps.config.ZPSConfig} instead.
 */
public final class ReactorTuning {

    private ReactorTuning() { }

    /** Thermal mass of one wall block, in joules per kelvin. */
    public static final double WALL_HEAT_CAPACITY_J_PER_K = 150.0;

    /** A chamber holding no more than this of every gas, in kilograms, counts as empty. */
    public static final double EMPTY_GAS_THRESHOLD_KG = 0.001;

    /** How long a chamber may sit empty before it starts losing heat, in ticks. */
    public static final int EMPTY_GRACE_TICKS = 60;

    /** The share of an empty chamber's excess over ambient that it loses each tick. */
    public static final double EMPTY_COOLING_FRACTION = 0.005;

    /** Gas one Exhaust Port can draw out of the chamber, in kilograms per tick. */
    public static final double EXHAUST_KG_PER_TICK = 0.005;

    /** Temperature an Exhaust Port cools released gas to, in kelvin. Below what a Gas Duct can carry. */
    public static final double EXHAUST_OUTLET_TEMPERATURE_K = 1000.0;

    /** An Exhaust Port stops drawing once its own outlet reaches this pressure, in pascals. */
    public static final double EXHAUST_BACKPRESSURE_LIMIT_PA = 8_000_000.0;

    /** Extra explosion radius per 100% over the burst pressure. */
    public static final double BURST_RADIUS_PER_OVERSHOOT = 4.0;

    /** Radius around a melt breach that is set alight, in blocks. */
    public static final int BREACH_FIRE_RADIUS = 2;

    /** Radius around a melt breach in which creatures are burned, in blocks. */
    public static final int BREACH_IGNITE_RADIUS = 4;

    /** Players within this many blocks of a reactor earn its advancements. */
    public static final int ADVANCEMENT_RADIUS = 32;
}
