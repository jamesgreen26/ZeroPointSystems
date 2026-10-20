package g_mungus.zps.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.entity.ControllerSeatEntity;
import g_mungus.zps.networking.ControllerInputC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The rider's side of the Octo- and Dodeca-Controller.
 *
 * <p>The keys are the controller's own, not entries in the controls menu, so they are read straight
 * off the keyboard. While they drive the controller they are shadowed: any key mapping on the same
 * key reads as released and swallows its clicks (see {@code KeyMappingMixin}), so holding W does not
 * also walk and a bound E does not open the inventory. Use is never shadowed, because using the
 * controller again is how the rider gets off, and clicks otherwise work as normal while riding.
 */
public final class ControllerClientHooks {
    private ControllerClientHooks() {}

    /** The last pressed mask sent, or -1 when nothing has been sent for this ride yet. */
    private static int lastSent = -1;
    /** The controller {@link #lastSent} went to: a seat moved to another dimension finds a new one. */
    private static @Nullable ControllerBlockEntity<?> lastController = null;
    /**
     * Ticks between resending an unchanged mask. One sent while the server's seat is between
     * dimensions, or has not found its controller there yet, is dropped; without this a key let go
     * then would stay held until pressed again.
     */
    private static final int RESEND_TICKS = 10;
    private static int sinceSent = 0;

    /**
     * Whether use has been let go since boarding. The click that boards is usually still held for a
     * few ticks after, and vanilla repeats use while it is held, so without this the repeat would hit
     * the controller again and throw the rider straight back off.
     */
    private static boolean dismountArmed = false;

    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof ControllerBlockEntity<?> controller)) return;
        minecraft.setScreen(new ControllerKeysScreen(pos, controller));
    }

    private static @Nullable ControllerBlockEntity<?> ridden() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof ControllerSeatEntity seat) {
            return seat.blockEntity;
        }
        return null;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ControllerBlockEntity<?> controller = ridden();
        if (controller == null) {
            lastSent = -1;
            lastController = null;
            dismountArmed = false;
            return;
        }
        if (!minecraft.options.keyUse.isDown()) {
            dismountArmed = true;
        }

        int pressed = 0;
        if (minecraft.screen == null) {
            long window = minecraft.getWindow().getWindow();
            for (int i = 0; i < controller.channelCount(); i++) {
                int key = controller.getKey(i);
                if (key != ControllerBlockEntity.UNBOUND && InputConstants.isKeyDown(window, key)) {
                    pressed |= 1 << i;
                }
            }
        }
        if (pressed != lastSent || controller != lastController || ++sinceSent >= RESEND_TICKS) {
            lastSent = pressed;
            lastController = controller;
            sinceSent = 0;
            ZPSGamePackets.sendToServer(new ControllerInputC2SPacket(pressed));
        }
    }

    /**
     * Drops the held repeat of the boarding click when it lands on the controller being ridden. Every
     * other click goes through untouched; a fresh one on the controller gets the rider off, which the
     * block itself handles.
     */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || dismountArmed) return;
        ControllerBlockEntity<?> controller = ridden();
        if (controller == null) return;
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(controller.getBlockPos())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Whether a key mapping on this key is standing aside for the controller being ridden. */
    public static boolean isShadowed(KeyMapping mapping, InputConstants.Key key) {
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        ControllerBlockEntity<?> controller = ridden();
        if (controller == null || mapping == Minecraft.getInstance().options.keyUse) return false;
        for (int i = 0; i < controller.channelCount(); i++) {
            if (controller.getKey(i) == key.getValue()) return true;
        }
        return false;
    }
}
