package g_mungus.zps.blockentity.gas.core;

import g_mungus.zps.block.gas.core.KNodeBlock;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.INodeBlockEntity;

/**
 * Java-side view of Kelvin's {@link INodeBlockEntity}, forwarding to the Kotlin-generated
 * {@code DefaultImpls} for the same reason {@link KNodeBlock} does.
 */
public interface KNodeBlockEntity extends INodeBlockEntity {

    @Override
    @NotNull DuctNodePos getDuctNodePosition();

    @Override
    default boolean ensureNodeExists() {
        return INodeBlockEntity.DefaultImpls.ensureNodeExists(this);
    }

    @Override
    default void loadData(@NotNull CompoundTag compoundTag, @NotNull DuctNodePos ductNodePos, boolean client) {
        INodeBlockEntity.DefaultImpls.loadData(this, compoundTag, ductNodePos, client);
    }

    @Override
    default void saveData(@NotNull CompoundTag compoundTag, @NotNull DuctNodePos ductNodePos, boolean client) {
        INodeBlockEntity.DefaultImpls.saveData(this, compoundTag, ductNodePos, client);
    }
}
