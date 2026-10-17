package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.CommandDispatcher;
import g_mungus.zps.client.script.ClientScripts;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.zps.client.DuctTravelFade;
import g_mungus.zps.entity.ControllerSeatEntity;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Grafts the script tree into chat's command dispatcher (see {@link ClientScripts}), and swaps
 * vanilla's "Press Shift to Dismount" banner for the duct's own controls when the thing being
 * boarded is a duct, and for "use again" when it is an Octo- or Dodeca-Controller.
 *
 * <p>{@code handleSetEntityPassengersPacket} posts that message for every vehicle a player boards,
 * with nothing to hook and no way to change it. Inside a duct there is more to know than how to
 * get out — the same keys that would steer a mount instead choose the next vent — so the banner is
 * kept, in the same place and for the same moment, and made to say so.
 *
 * <p>For a duct the narration on the following line is deliberately left alone: sneaking really does
 * climb the player out, so for anyone listening rather than reading that announcement is still
 * correct. A controller ignores sneaking, so its narration is swapped as well.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin implements ClientScripts.ScriptCommandsHolder {

    @Shadow
    private CommandDispatcher<SharedSuggestionProvider> commands;

    @Shadow
    public abstract net.minecraft.core.RegistryAccess registryAccess();

    /** 1.20.1 sends the registries in the login packet; the biomes there need their atmospheres too. */
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void zps$attachAtmosphericComposition(net.minecraft.network.protocol.game.ClientboundLoginPacket packet, CallbackInfo ci) {
        g_mungus.zps.world.AtmosphericComposition.attachToBiomes(registryAccess());
    }

    /** Each commands packet makes a new dispatcher, so the scripts are grafted into every one. */
    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void zps$graftScriptsAfterCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        zps$graftScripts();
    }

    @Override
    public void zps$graftScripts() {
        commands = ClientScripts.graft(commands);
    }

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
            // A duct being announced mid-hop is one the tracker dropped and has just sent back,
            // not one the player has just climbed into: they were told the controls on the way in.
            if (DuctTravelFade.isTransitioning()) {
                return;
            }
            Options options = minecraft.options;
            message = Component.translatable("zps.duct.mount_hint",
                    options.keyShift.getTranslatedKeyMessage(),
                    options.keyLeft.getTranslatedKeyMessage(),
                    options.keyRight.getTranslatedKeyMessage());
        }
        if (player != null && player.getVehicle() instanceof ControllerSeatEntity) {
            message = zps$controllerHint(minecraft);
        }
        original.call(gui, message, animate);
    }

    /** Unlike a duct, a controller does not let go on sneaking, so the narration has to change too. */
    @WrapOperation(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/GameNarrator;sayNow(Lnet/minecraft/network/chat/Component;)V"))
    private void zps$controllerNarration(GameNarrator narrator, Component message, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof ControllerSeatEntity) {
            message = zps$controllerHint(minecraft);
        }
        original.call(narrator, message);
    }

    @Unique
    private static Component zps$controllerHint(Minecraft minecraft) {
        return Component.translatable("zps.controller.mount_hint",
                minecraft.options.keyUse.getTranslatedKeyMessage());
    }
}
