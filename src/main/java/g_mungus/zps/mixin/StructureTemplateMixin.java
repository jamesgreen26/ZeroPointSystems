package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.blockentity.NetworkTerminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {

    @WrapOperation(
            method = "placeInWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"
            )
    )
    public void zps$wrapBlockEntityLoad(
            BlockEntity blockEntity,
            CompoundTag tag,
            HolderLookup.Provider registries,
            Operation<Void> operation,
            ServerLevelAccessor levelAccessor,
            BlockPos pos0,
            BlockPos pos1,
            StructurePlaceSettings settings,
            RandomSource randomSource,
            int flags
    ) {
        CompoundTag resultTag = tag;

        if (blockEntity instanceof NetworkTerminal) {
            resultTag = NetworkTerminal.transformTag(tag, settings.getMirror(), settings.getRotation());
        }

        operation.call(blockEntity, resultTag, registries);
    }
}
