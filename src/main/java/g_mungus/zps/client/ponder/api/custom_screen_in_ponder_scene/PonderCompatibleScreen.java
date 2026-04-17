package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class PonderCompatibleScreen extends Screen {

    @FunctionalInterface
    public interface ScreenOverlay {
        void render(GuiGraphics graphics, float partialTicks);
    }

    private final List<ScreenOverlay> ponderOverlays = new ArrayList<>();

    public void addPonderOverlay(ScreenOverlay overlay) {
        ponderOverlays.add(overlay);
    }

    public void removePonderOverlay(ScreenOverlay overlay) {
        ponderOverlays.remove(overlay);
    }

    public void setShouldRenderBackground(boolean shouldRenderBackground) {
        this.shouldRenderBackground = shouldRenderBackground;
    }

    private boolean shouldRenderBackground;
    private boolean isInPonder = false;

    protected PonderCompatibleScreen(Component arg) {
        super(arg);
    }


    public void forceRenderBackground(GuiGraphics arg) {
        assert this.minecraft != null;
        if (this.minecraft.level != null) {
            arg.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        } else {
            this.renderMenuBackground(arg);
        }
    }

    @Override
    public void renderBackground(GuiGraphics arg, int mouseX, int mouseY, float partialTick) {
        if (shouldRenderBackground) {
            super.renderBackground(arg, mouseX, mouseY, partialTick);
        }
    }

    public boolean isInPonder() {
        return isInPonder;
    }

    public void setInPonder(boolean inPonder) {
        isInPonder = inPonder;
    }


    public void renderPonder(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        render(graphics, mouseX, mouseY, partialTicks);
        for (ScreenOverlay overlay : ponderOverlays) {
            overlay.render(graphics, partialTicks);
        }
    }
}
