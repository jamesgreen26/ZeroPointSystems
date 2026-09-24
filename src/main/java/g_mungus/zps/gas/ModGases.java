package g_mungus.zps.gas;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * The gases ZPS adds to Kelvin. Kelvin itself ships only {@code kelvin:air}.
 *
 * <p>Each gas is paired with a particle type registered in ZPS's own namespace, so the texture
 * list lives at {@code assets/zps/particles/<gas>.json}.
 */
public final class ModGases {

    private ModGases() {
    }

    /**
     * Flux density at STP, in kg/m^3. A compile-time constant so {@link ModParticles} can read it
     * for the particle's buoyancy without triggering this class's initializer — {@code ModGases}
     * already depends on {@code ModParticles}, and a runtime read would close that cycle.
     */
    public static final double FLUX_DENSITY = 0.0899;

    /** Densities of the Aether and Steam stand-ins, for their particles; see {@link Compat#getOrCreateAetherGas}. */
    public static final double AETHER_DENSITY = 0.166;
    public static final double STEAM_DENSITY = 0.762;

    /**
     * Fusion fuel, vaporized from blue ice and lithium. Hydrogen-like: very light, very high heat
     * capacity. These are placeholder figures — the design doc still owns picking real ones.
     */
    public static final GasType FLUX = new GasType(
            "Flux",
            ZPSMod.resource("flux"),
            FLUX_DENSITY,
            0.88e-5,  // dynamic viscosity, kg/(m*s)
            14.30,    // specific heat capacity, J/(K*g)
            0.18,     // thermal conductivity, W/(m*K)
            72.0,     // Sutherland constant
            1.4,      // adiabatic index
            GasType.Companion.getPLACEHOLDER_ICON());

    public static final GasType AETHER = Compat.getOrCreateAetherGas();

    /** Water vapour, the bulk of what vaporizing ice gives off. Clockwork's when it is loaded. */
    public static final GasType STEAM = Compat.getOrCreateSteamGas();

    /**
     * Registers ZPS's gases with Kelvin. Safe to call during mod construction: gas types live in
     * Kelvin's {@code kelvin:gas_type} registry, fed by an Architectury deferred register. Entries
     * added after Kelvin has submitted that register are queued until {@code RegisterEvent}, which
     * fires once every mod has been constructed, so the order ZPS and Kelvin construct in does not
     * matter. The {@link GasType} instances are registered as-is, so the constants above stay the
     * canonical objects; the registry only becomes queryable ({@code getGasType}) after
     * {@code RegisterEvent}. The particle picker resolves its type lazily.
     *
     * <p>Deliberately never the single-argument {@code register(GasType)}: that one registers a
     * particle into Kelvin's own deferred registry, which needs Kelvin's mod event bus. Forge
     * constructs mods in parallel, so Kelvin may not have registered its bus yet when this runs,
     * and an entry added after Kelvin has submitted the register never resolves anyway. ZPS
     * registers its particle types itself — see {@link ModParticles}.
     */
    public static void register() {
        GasTypeRegistry.INSTANCE.register(FLUX, new LazyGasParticlePicker(ModParticles.FLUX));

        if (!Compat.isClockworkLoaded()) {
            GasTypeRegistry.INSTANCE.register(AETHER, new LazyGasParticlePicker(ModParticles.AETHER));
            GasTypeRegistry.INSTANCE.register(STEAM, new LazyGasParticlePicker(ModParticles.STEAM));
        }
    }
}
