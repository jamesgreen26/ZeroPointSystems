package g_mungus.zps.mixin.ponder;

import net.createmod.ponder.foundation.PonderScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ponder fixes a scene's scale when it is built; {@code SceneZoomElement} moves it mid-scene. */
@Mixin(value = PonderScene.class, remap = false)
public interface PonderSceneAccessor {
    @Accessor(value = "scaleFactor", remap = false)
    void zps$setScaleFactor(float scaleFactor);
}
