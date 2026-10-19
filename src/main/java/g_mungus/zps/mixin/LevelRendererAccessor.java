package g_mungus.zps.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SortedSet;

/**
 * Reads which blocks are being mined, so a multiblock's renderer can crack the whole thing (for the
 * Large Space Door), and the render tick count that Flywheel's shaders see as {@code flw_ticks}.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("destructionProgress")
    Long2ObjectMap<SortedSet<BlockDestructionProgress>> zps$getDestructionProgress();

    @Accessor("ticks")
    int zps$getTicks();
}
