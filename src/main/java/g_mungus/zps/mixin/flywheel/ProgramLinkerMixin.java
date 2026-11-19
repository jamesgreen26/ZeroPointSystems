package g_mungus.zps.mixin.flywheel;

import dev.engine_room.flywheel.backend.compile.core.ProgramLinker;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Pins Flywheel's colour output to location 0 before every program links, as Flywheel's 1.20.1
 * builds do themselves. The exhaust fragment shader writes its revealage to location 1 of the same
 * program (see exhaust.frag), and with that taken the linker may put an output with no location
 * anywhere free; the GL spec does not promise 0. Programs without the output ignore the binding.
 * Optional: if the call moves, linkers in practice still choose 0.
 */
@Mixin(value = ProgramLinker.class, remap = false)
public abstract class ProgramLinkerMixin {

    @ModifyArg(method = "linkInternal", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL20;glLinkProgram(I)V"), require = 0)
    private int zps$pinOutputColor(int program) {
        GL30.glBindFragDataLocation(program, 0, "_flw_outputColor");
        return program;
    }
}
