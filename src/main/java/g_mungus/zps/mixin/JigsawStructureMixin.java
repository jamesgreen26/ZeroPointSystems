package g_mungus.zps.mixin;

import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(JigsawStructure.class)
public class JigsawStructureMixin {

    @ModifyArg(
            method = "lambda$static$7",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;",
                    ordinal = 0 // first call = intRange(0, 7)
            ),
            index = 1 // second argument (the 7)
    )
    private static int zps$increaseMaxDepthLimit(int originalMax) {
        return 20;
    }

    @ModifyArg(
            method = "lambda$static$7",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;",
                    ordinal = 1 // second call = intRange(0, 128)
            ),
            index = 1 // second argument (the 128)
    )
    private static int zps$increaseMaxDistanceLimit(int originalMax) {
        return 256;
    }


    @ModifyConstant(
            method = "verifyRange",
            constant = @Constant(intValue = 128)
    )
    private static int zps$increaseMaxDistanceLimit1(int original) {
        return 256;
    }
}