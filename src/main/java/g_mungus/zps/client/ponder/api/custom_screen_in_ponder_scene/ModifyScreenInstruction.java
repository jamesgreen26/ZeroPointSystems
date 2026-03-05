package g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;

import java.util.function.Consumer;

public class ModifyScreenInstruction extends PonderInstruction {

    private final Runnable runnable;

    public ModifyScreenInstruction(ScreenPonderElement element, Consumer<PonderCompatibleScreen> consumer) {
        runnable = () -> {
            consumer.accept(element.screen);
        };
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(PonderScene scene) {
        runnable.run();
    }
}
