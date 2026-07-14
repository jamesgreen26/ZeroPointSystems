package g_mungus.zps.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Implemented by container screens that want to draw some of their slots' contents themselves (e.g. as a
 * translucent ghost/preview) instead of letting vanilla render them opaquely.
 *
 * <p>Forge 1.20.1 has no per-slot {@code renderSlotContents} hook (that was added in a later version), so
 * {@code AbstractContainerScreenMixin} calls this from the head of {@code renderSlot} and cancels vanilla's
 * draw when the screen reports it handled the slot.
 */
public interface GhostSlotRenderer {
    /**
     * @return true if this screen fully rendered the slot's contents (vanilla's opaque draw is then skipped);
     * false to let vanilla render the slot normally.
     */
    boolean zps$renderGhostSlot(GuiGraphics graphics, Slot slot);
}
