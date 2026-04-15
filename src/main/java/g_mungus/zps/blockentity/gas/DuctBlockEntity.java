package g_mungus.zps.blockentity.gas;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.KNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

public class DuctBlockEntity extends BlockEntity implements KNodeBlockEntity {

    public DuctBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(ModBlockEntities.DUCT.get(), p_155229_, p_155230_);
    }

    @Override
    public @NotNull DuctNodePos getDuctNodePosition() {
        ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");
        if (level != null) {
            dimension = level.dimension().location();
        }
        return KelvinExtensions.INSTANCE.toDuctNodePos(getBlockPos(), dimension);
    }

    @Override
    public void saveData(@NotNull CompoundTag compoundTag, @NotNull DuctNodePos ductNodePos, boolean client) {
        KNodeBlockEntity.super.saveData(compoundTag, ductNodePos, client);
    }

    @Override
    public void loadData(@NotNull CompoundTag compoundTag, @NotNull DuctNodePos ductNodePos, boolean client) {
        KNodeBlockEntity.super.loadData(compoundTag, ductNodePos, client);
    }
}
