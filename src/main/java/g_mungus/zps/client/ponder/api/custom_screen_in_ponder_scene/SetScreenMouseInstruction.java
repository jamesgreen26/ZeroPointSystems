package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import com.mojang.blaze3d.platform.Window;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;

import java.util.function.BiFunction;

public class SetScreenMouseInstruction extends PonderInstruction {

    private final ScreenPonderElement screenElement;
    private final BiFunction<Integer, Integer, Vec2> positionFn;

    /**
     * Sets the mouse position over the screen to a layout-relative virtual coordinate.
     * {@code positionFn} receives the screen's current virtual width and height and returns
     * a position in virtual screen coordinates, which is then converted to PonderUI mouse
     * coordinates using the same scale math as {@link ScreenPonderElement#render}.
     */
    public SetScreenMouseInstruction(ScreenPonderElement screenElement, BiFunction<Integer, Integer, Vec2> positionFn) {
        this.screenElement = screenElement;
        this.positionFn = positionFn;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(PonderScene scene) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        int maxScale = window.calculateScale(Integer.MAX_VALUE, mc.isEnforceUnicode());
        int targetScale = Math.max(1, maxScale / 2);
        double currentScale = window.getGuiScale();

        Vec2 virtualPos = positionFn.apply(screenElement.screen.width, screenElement.screen.height);
        int mouseX = (int)(virtualPos.x * targetScale / currentScale);
        int mouseY = (int)(virtualPos.y * targetScale / currentScale);
        screenElement.setMouse(mouseX, mouseY);
    }
}
