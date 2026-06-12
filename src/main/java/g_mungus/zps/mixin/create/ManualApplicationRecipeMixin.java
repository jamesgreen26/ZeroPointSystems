package g_mungus.zps.mixin.create;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.item.ModItems;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe", remap = false)
public class ManualApplicationRecipeMixin {
    @Inject(method = "manualApplicationRecipesApplyInWorld", at = @At("HEAD"), cancellable = true)
    private static void zps$skipIncompleteRoboticArmAssembly(PlayerInteractEvent.RightClickBlock event,
                                                            CallbackInfo ci) {
        if (!event.getItemStack().is(ModItems.ROBOTIC_ARM_SEGMENT.get())) {
            return;
        }

        var state = event.getLevel().getBlockState(event.getPos());
        if (state.is(ModBlocks.INCOMPLETE_ROBOTIC_ARM_0.get())
                || state.is(ModBlocks.INCOMPLETE_ROBOTIC_ARM_1.get())
                || state.is(ModBlocks.INCOMPLETE_ROBOTIC_ARM_2.get())) {
            ci.cancel();
        }
    }
}
