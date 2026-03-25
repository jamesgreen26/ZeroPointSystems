package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.zps.block.cableNetwork.core.CableNetworkComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {

    @Inject(
            method = "placeInWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;load(Lnet/minecraft/nbt/CompoundTag;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void zps$afterBlockEntityLoad(
            ServerLevelAccessor p_230329_,
            BlockPos p_230330_,
            BlockPos p_230331_,
            StructurePlaceSettings p_230332_,
            RandomSource p_230333_,
            int p_230334_,
            CallbackInfoReturnable<Boolean> cir,
            @Local(name = "blockentity1") BlockEntity blockEntity
    ) {
        BlockState state = p_230329_.getBlockState(blockEntity.getBlockPos());
        if (state.getBlock() instanceof CableNetworkComponent cableComponent && cableComponent.isTerminal()) {
            cableComponent.updateNetwork(blockEntity.getBlockPos(), p_230329_.getLevel());
        }
    }
}
