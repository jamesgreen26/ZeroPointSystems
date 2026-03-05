package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.FadeInOutInstruction;

public class ShowScreenInstruction extends FadeInOutInstruction {
    private final ScreenPonderElement element;

    public ShowScreenInstruction(ScreenPonderElement element, int ticks) {
        super(ticks);
        this.element = element;
    }

    @Override
    protected void show(PonderScene scene) {
        scene.addElement(element);
        element.setVisible(true);
    }

    @Override
    protected void hide(PonderScene scene) {
        element.setVisible(false);
    }

    @Override
    protected void applyFade(PonderScene scene, float fade) {
    }
}
