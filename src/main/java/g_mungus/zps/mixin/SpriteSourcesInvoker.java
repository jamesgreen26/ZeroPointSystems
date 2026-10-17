package g_mungus.zps.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Forge 1.20.1 has no event for adding sprite source types, so ZPS registers its own here. */
@Mixin(SpriteSources.class)
public interface SpriteSourcesInvoker {
    @Invoker("register")
    static SpriteSourceType zps$register(String name, Codec<? extends SpriteSource> codec) {
        throw new AssertionError();
    }
}
