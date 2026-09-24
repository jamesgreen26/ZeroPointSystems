package g_mungus.zps.client;

import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The readout drawn while a player is inside a duct: which vent they are peeking out of, how many
 * there are on this run, and how to work it.
 *
 * <p>Deliberately a strip above the hotbar rather than a screen. The player is still in the world
 * and still turning their head; taking the mouse away to pick a destination would break that.
 */
public final class DuctTravelOverlay implements IGuiOverlay {

    public static final DuctTravelOverlay INSTANCE = new DuctTravelOverlay();

    /**
     * Clear of vanilla's overlay message, which sits at 68 above the bottom and is where the duct's
     * own controls banner appears on the way in.
     */
    private static final int PANEL_BOTTOM_MARGIN = 90;
    private static final int TITLE_COLOR = 0xFFAAAAAA;

    private DuctTravelOverlay() {
    }

    @Override
    public void render(@NotNull ForgeGui gui, @NotNull GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // Not while on the move: the count is about the vent being looked out of, and between
        // vents there is none. The progress bar carries the crawl instead.
        if (player == null
                || minecraft.options.hideGui
                || !(player.getVehicle() instanceof DuctTravelEntity)
                || DuctTravelClientState.isEmpty()
                || DuctTravelFade.isTransitioning()) {
            return;
        }

        List<BlockPos> destinations = DuctTravelClientState.destinations();
        int selected = Mth.clamp(DuctTravelClientState.selected(), 0, destinations.size() - 1);
        Component title = Component.translatable("zps.duct.hud.title",
                selected + 1, destinations.size());

        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        int top = graphics.guiHeight() - PANEL_BOTTOM_MARGIN;


        graphics.drawCenteredString(font, title, centerX, top, TITLE_COLOR);
    }
}
