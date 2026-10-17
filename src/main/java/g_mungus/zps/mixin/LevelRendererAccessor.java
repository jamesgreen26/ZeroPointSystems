package g_mungus.zps.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SortedSet;

/** Reads which blocks are being mined, so a multiblock's renderer can crack the whole thing. For the Large Space Door. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("destructionProgress")
    Long2ObjectMap<SortedSet<BlockDestructionProgress>> zps$getDestructionProgress();
}
