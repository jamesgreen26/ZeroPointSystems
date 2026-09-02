package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps vanilla's "Press Shift to Dismount" banner off the screen when the thing being boarded is
 * a duct.
 *
 * <p>{@code handleSetEntityPassengersPacket} posts that message for every vehicle a player boards,
 * with nothing to hook and no way to opt out. A duct already says the same thing in its own HUD,
 * next to the keys for choosing a vent, so vanilla's banner is a duplicate sitting over the top of
 * it — and it arrives during the fade in, where nothing else is on screen yet.
 *
 * <p>The narration on the following line is deliberately left alone: sneaking really does climb the
 * player out, so for anyone listening rather than reading that announcement is still correct, and
 * it is the only form the duct's own hint does not already take.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @WrapWithCondition(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private boolean zps$hideDismountPromptInDucts(Gui gui, Component message, boolean animate) {
        // startRiding has already run by here, so the vehicle is the one being announced.
        LocalPlayer player = Minecraft.getInstance().player;
        return !(player != null && player.getVehicle() instanceof DuctTravelEntity);
    }
}
