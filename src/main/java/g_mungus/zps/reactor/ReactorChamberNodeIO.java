package g_mungus.zps.reactor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.DuctNodeInfo;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.Map;

/**
 * Saves and restores a chamber's contents. Kelvin persists nothing itself, so the reactor keeps
 * this in its own save data and puts it back after the node is recreated.
 *
 * <p>Restoring writes the node's info directly, the way Kelvin's own block entity load does.
 * {@code modTemperature} is additive and a fresh node starts at ambient, so going through the
 * public mutators would leave the chamber hotter than it was saved.
 */
public final class ReactorChamberNodeIO {

    private static final String ENERGY = "Energy";
    private static final String TEMPERATURE = "Temperature";
    private static final String PRESSURE = "Pressure";
    private static final String GASES = "Gases";

    private ReactorChamberNodeIO() {
    }

    public static CompoundTag save(DuctNetwork<?> kelvin, DuctNodePos host) {
        CompoundTag tag = new CompoundTag();
        CompoundTag gases = new CompoundTag();
        for (Map.Entry<GasType, Double> entry : kelvin.getGasMassAt(host).entrySet()) {
            gases.putDouble(entry.getKey().getResourceLocation().toString(), entry.getValue());
        }
        tag.put(GASES, gases);
        tag.putDouble(ENERGY, kelvin.getHeatEnergy(host));
        tag.putDouble(TEMPERATURE, kelvin.getTemperatureAt(host));
        tag.putDouble(PRESSURE, kelvin.getPressureAt(host));
        return tag;
    }

    /** Apply saved contents to a node that already exists. Does nothing if it does not. */
    public static void restore(DuctNetwork<?> kelvin, DuctNodePos host, CompoundTag tag) {
        DuctNodeInfo info = kelvin.getNodeInfo().get(host);
        if (info == null) {
            return;
        }

        info.getCurrentGasMasses().clear();
        CompoundTag gases = tag.getCompound(GASES);
        for (String key : gases.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            GasType gas = id == null ? null : GasTypeRegistry.INSTANCE.getGasType(id);
            if (gas != null) {
                info.getCurrentGasMasses().put(gas, gases.getDouble(key));
            }
        }
        info.setCurrentEnergy(tag.getDouble(ENERGY));
        info.setCurrentTemperature(tag.getDouble(TEMPERATURE));
        info.setCurrentPressure(tag.getDouble(PRESSURE));
    }
}
