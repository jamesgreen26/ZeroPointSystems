package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import com.mojang.blaze3d.platform.Window;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ScreenPonderElement implements PonderOverlayElement {

    private boolean visible = false;
    public PonderCompatibleScreen screen;
    private final Supplier<PonderCompatibleScreen> screenSupplier;
    private final List<PonderCompatibleScreen.ScreenOverlay> overlays = new ArrayList<>();

    private int mouseX = 0;
    private int mouseY = 0;

    private int lastVirtualWidth = -1;
    private int lastVirtualHeight = -1;

    public ScreenPonderElement(Supplier<PonderCompatibleScreen> screenSupplier) {
        this.screenSupplier = screenSupplier;
        reset();
    }

    @Override
    public void reset(@NotNull PonderScene scene) {
        reset();
    }

    public void reset() {
        this.screen = screenSupplier.get();
        screen.setShouldRenderBackground(false);
        screen.setInPonder(true);
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        int maxScale = window.calculateScale(0, mc.isEnforceUnicode());
        int targetScale = Math.max(1, maxScale / 2);

        int virtualWidth  = window.getWidth()  / targetScale;
        int virtualHeight = window.getHeight() / targetScale;

        screen.init(mc, virtualWidth, virtualHeight);

        for (PonderCompatibleScreen.ScreenOverlay overlay : overlays) {
            screen.addPonderOverlay(overlay);
        }
    }

    public void addOverlay(PonderCompatibleScreen.ScreenOverlay overlay) {
        overlays.add(overlay);
        screen.addPonderOverlay(overlay);
    }

    public void removeOverlay(PonderCompatibleScreen.ScreenOverlay overlay) {
        overlays.remove(overlay);
        screen.removePonderOverlay(overlay);
    }

    @Override
    public void render(@NotNull PonderScene scene,
                       @NotNull PonderUI ui,
                       @NotNull GuiGraphics graphics,
                       float partialTicks) {

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        int maxScale = window.calculateScale(Integer.MAX_VALUE, mc.isEnforceUnicode());
        int targetScale = Math.max(1, maxScale / 2);

        int virtualWidth  = window.getWidth()  / targetScale;
        int virtualHeight = window.getHeight() / targetScale;

        if (virtualWidth != lastVirtualWidth || virtualHeight != lastVirtualHeight) {
            screen.init(mc, virtualWidth, virtualHeight);
            lastVirtualWidth = virtualWidth;
            lastVirtualHeight = virtualHeight;
        }

        double currentScale = window.getGuiScale();

        double rawMouseX = mouseX * currentScale;
        double rawMouseY = mouseY * currentScale;

        graphics.pose().pushPose();

        // Remove current GUI scaling
        graphics.pose().scale((float)(1f / currentScale), (float)(1f / currentScale), 1f);

        // Apply our custom scale
        graphics.pose().scale(targetScale, targetScale, 1f);

//        this.screen.forceRenderBackground(graphics);

        graphics.pose().translate(0f, ((float) window.getHeight() / targetScale) / 5.5f, 0f);
        this.screen.renderPonder(
                graphics,
                (int)(rawMouseX / targetScale),
                (int)(rawMouseY / targetScale),
                partialTicks
        );

        graphics.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setMouse(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }
}
