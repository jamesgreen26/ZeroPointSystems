package g_mungus.zps.client;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.jetbrains.annotations.NotNull;

/**
 * The crawl between two vents, drawn where the experience bar is: the same treatment a horse's
 * jump gets, in the same place, with the same bar.
 *
 * <p>While the player is inside a vent the experience bar and its level give way to this, empty
 * while they sit and look out, filling over the crawl to the next vent. The fade to black sits
 * under the HUD, so the bar stays in view for the whole of the crawl it measures.
 */
public final class DuctTravelProgressBar implements IGuiOverlay {

    public static final DuctTravelProgressBar INSTANCE = new DuctTravelProgressBar();

    private static final ResourceLocation BACKGROUND_SPRITE =
            ZPSMod.resource("textures/gui/sprites/hud/duct_travel_background.png");
    private static final ResourceLocation PROGRESS_SPRITE =
            ZPSMod.resource("textures/gui/sprites/hud/duct_travel_progress.png");

    /** The bar's size and place, as vanilla lays out the experience and jump bars. */
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_BOTTOM_MARGIN = 29;

    private DuctTravelProgressBar() {
    }

    /**
     * Whether the player is in a vent, and so whether this bar stands in for the experience bar.
     * A duct that has dropped out from under them for a moment mid-hop still counts, so the
     * experience bar does not flicker back for the few ticks it is gone.
     */
    private static boolean isRiding(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null
                && (player.getVehicle() instanceof DuctTravelEntity || DuctTravelFade.isAwaitingVehicle());
    }

    /** Takes the experience bar (and the level it draws) off the screen while the player is in a vent. */
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                && isRiding(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void render(@NotNull ForgeGui gui, @NotNull GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !isRiding(minecraft)
                || !(minecraft.player.getVehicle() instanceof DuctTravelEntity duct)) {
            return;
        }

        float progress = duct.crawlProgress(partialTick);
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = graphics.guiHeight() - BAR_BOTTOM_MARGIN;

        graphics.blit(BACKGROUND_SPRITE, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        // One more than the width, as vanilla fills its bars, so a full bar reaches the far end.
        int filled = (int) (progress * (BAR_WIDTH + 1));
        if (filled > 0) {
            graphics.blit(PROGRESS_SPRITE, x, y, 0, 0, Math.min(filled, BAR_WIDTH), BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        }
    }
}
