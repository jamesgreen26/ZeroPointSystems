package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps vanilla's "Press Shift to Dismount" banner for the duct's own controls when the thing being
 * boarded is a duct.
 *
 * <p>{@code handleSetEntityPassengersPacket} posts that message for every vehicle a player boards,
 * with nothing to hook and no way to change it. Inside a duct there is more to know than how to
 * get out — the same keys that would steer a mount instead choose the next vent — so the banner is
 * kept, in the same place and for the same moment, and made to say so.
 *
 * <p>The narration on the following line is deliberately left alone: sneaking really does climb the
 * player out, so for anyone listening rather than reading that announcement is still correct.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @WrapOperation(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void zps$ductControlsInsteadOfDismountPrompt(Gui gui, Component message, boolean animate,
                                                         Operation<Void> original) {
        // startRiding has already run by here, so the vehicle is the one being announced.
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && player.getVehicle() instanceof DuctTravelEntity) {
            Options options = minecraft.options;
            message = Component.translatable("zps.duct.mount_hint",
                    options.keyShift.getTranslatedKeyMessage(),
                    options.keyLeft.getTranslatedKeyMessage(),
                    options.keyRight.getTranslatedKeyMessage());
        }
        original.call(gui, message, animate);
    }
}
