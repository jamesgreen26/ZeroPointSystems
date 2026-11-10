package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.gas.GasExposure;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The air bar shows while the player's head is under water or its air is short of full. A player
 * drowning in gas counts as under water, so the bar shows from the start, as it does in the sea,
 * rather than only once a turtle helmet's water breathing has run out.
 */
@Mixin(value = ForgeGui.class, remap = false)
public abstract class ForgeGuiMixin {

    @WrapOperation(
            method = "renderAir",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluidType(Lnet/minecraftforge/fluids/FluidType;)Z"))
    private boolean zps$airBarWhileDrowningInGas(Player player, FluidType type, Operation<Boolean> original) {
        return original.call(player, type) || GasExposure.isDrowningOnEitherSide(player);
    }
}
