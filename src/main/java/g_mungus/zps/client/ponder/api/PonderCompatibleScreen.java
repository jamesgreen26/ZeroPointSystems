package g_mungus.zps.client.ponder.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PonderCompatibleScreen extends Screen {

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
            this.renderDirtBackground(arg);
        }
    }

    @Override
    public void renderBackground(GuiGraphics arg) {
        if (shouldRenderBackground) {
            super.renderBackground(arg);
        }
    }

    public boolean isInPonder() {
        return isInPonder;
    }

    public void setInPonder(boolean inPonder) {
        isInPonder = inPonder;
    }
}
