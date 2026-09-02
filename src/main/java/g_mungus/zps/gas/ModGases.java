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
     * Fusion fuel, vaporized from blue ice and lithium. Hydrogen-like: very light, very high heat
     * capacity. These are placeholder figures — the design doc still owns picking real ones.
     */
    public static final GasType FLUX = new GasType(
            "Flux",
            ZPSMod.resource("flux"),
            0.0899,   // density at STP, kg/m^3
            0.88e-5,  // dynamic viscosity, kg/(m*s)
            14.30,    // specific heat capacity, J/(K*g)
            0.18,     // thermal conductivity, W/(m*K)
            72.0,     // Sutherland constant
            1.4,      // adiabatic index
            GasType.Companion.getPLACEHOLDER_ICON());

    public static final GasType AETHER = Compat.getOrCreateAetherGas();

    /**
     * Registers ZPS's gases with Kelvin. Safe to call during mod construction — {@code
     * GasTypeRegistry} is a plain map, and the particle picker resolves its type lazily.
     *
     * <p>Deliberately not the single-argument {@code register(GasType)}: that one registers a
     * particle into Kelvin's own deferred registry, which Kelvin has already submitted by the time
     * another mod's constructor runs, leaving an entry that never resolves and crashes the client.
     * ZPS registers its particle types itself — see {@link ModParticles}.
     */
    public static void register() {
        GasTypeRegistry.INSTANCE.register(FLUX, new LazyGasParticlePicker(ModParticles.FLUX));

        if (!Compat.isClockworkLoaded()) {
            GasTypeRegistry.INSTANCE.register(AETHER);
        }
    }
}
