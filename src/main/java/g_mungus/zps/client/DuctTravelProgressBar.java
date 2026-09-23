package g_mungus.zps.client;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

/**
 * The crawl between two vents, drawn where the experience bar is: the same treatment a horse's
 * jump gets, in the same place, with the same bar.
 *
 * <p>While the player is inside a vent the experience bar and its level give way to this, empty
 * while they sit and look out, filling over the crawl to the next vent. The fade to black sits
 * under the HUD, so the bar stays in view for the whole of the crawl it measures.
 */
public final class DuctTravelProgressBar implements LayeredDraw.Layer {

    public static final DuctTravelProgressBar INSTANCE = new DuctTravelProgressBar();

    private static final ResourceLocation BACKGROUND_SPRITE =
            ZPSMod.resource("hud/duct_travel_background");
    private static final ResourceLocation PROGRESS_SPRITE =
            ZPSMod.resource("hud/duct_travel_progress");

    /** The bar's size and place, as vanilla lays out the experience and jump bars. */
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_BOTTOM_MARGIN = 29;

    private DuctTravelProgressBar() {
    }

    /** Whether the player is in a vent, and so whether this bar stands in for the experience bar. */
    private static boolean isRiding(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.getVehicle() instanceof DuctTravelEntity;
    }

    /** Takes the experience bar and level off the screen while the player is in a vent. */
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation name = event.getName();
        if ((name.equals(VanillaGuiLayers.EXPERIENCE_BAR) || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
                && isRiding(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !isRiding(minecraft)
                || !(minecraft.player.getVehicle() instanceof DuctTravelEntity duct)) {
            return;
        }

        float progress = duct.crawlProgress(deltaTracker.getGameTimeDeltaPartialTick(false));
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = graphics.guiHeight() - BAR_BOTTOM_MARGIN;

        graphics.blitSprite(BACKGROUND_SPRITE, x, y, BAR_WIDTH, BAR_HEIGHT);
        // One more than the width, as vanilla fills its bars, so a full bar reaches the far end.
        int filled = (int) (progress * (BAR_WIDTH + 1));
        if (filled > 0) {
            graphics.blitSprite(PROGRESS_SPRITE, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        }
    }
}
