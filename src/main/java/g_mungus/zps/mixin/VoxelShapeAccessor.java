package g_mungus.zps.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The cell grid behind any {@link VoxelShape}. */
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    @Accessor("shape")
    DiscreteVoxelShape zps$getShape();
}
