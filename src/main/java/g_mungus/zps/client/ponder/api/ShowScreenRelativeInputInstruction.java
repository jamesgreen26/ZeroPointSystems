package g_mungus.zps.client.ponder.api;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.FadeInOutInstruction;

public class ShowScreenRelativeInputInstruction extends FadeInOutInstruction {

    private final ScreenElement screenElement;
    private final ScreenSpaceInputWindowElement inputElement;
    private float currentFade = 0f;
    private PonderCompatibleScreen.ScreenOverlay overlay;

    public ShowScreenRelativeInputInstruction(ScreenElement screenElement, ScreenSpaceInputWindowElement inputElement, int ticks) {
        super(ticks);
        this.screenElement = screenElement;
        this.inputElement = inputElement;
    }

    @Override
    protected void show(PonderScene scene) {
        overlay = (graphics, partialTicks) -> inputElement.renderInScreenSpace(graphics, partialTicks, currentFade);
        screenElement.addOverlay(overlay);
    }

    @Override
    protected void hide(PonderScene scene) {
        screenElement.removeOverlay(overlay);
    }

    @Override
    protected void applyFade(PonderScene scene, float fade) {
        currentFade = fade;
    }
}
