package g_mungus.zps.blockentity.gas;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.KNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

public class DuctBlockEntity extends BlockEntity implements KNodeBlockEntity {

    public DuctBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(ModBlockEntities.DUCT.get(), p_155229_, p_155230_);
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);

        if (level instanceof ServerLevel) {
            KelvinMod.INSTANCE.forceGetKelvin().markLoaded(this.getDuctNodePosition());
        }
    }


    @Override
    public void setRemoved() {
        super.setRemoved();
        KelvinMod.INSTANCE.getKelvinClient().removeNode(this.getDuctNodePosition());
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
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        if (level != null) {
            saveData(tag, getDuctNodePosition(), level.isClientSide);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        if (level != null) {
            loadData(tag, getDuctNodePosition(), level.isClientSide);
        }
    }
}
