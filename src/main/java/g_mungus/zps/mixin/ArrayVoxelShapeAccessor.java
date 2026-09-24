package g_mungus.zps.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The coordinate lists behind an {@link ArrayVoxelShape}, and its package-private constructor. */
@Mixin(ArrayVoxelShape.class)
public interface ArrayVoxelShapeAccessor {

    @Accessor("xs")
    DoubleList zps$getXs();

    @Accessor("ys")
    DoubleList zps$getYs();

    @Accessor("zs")
    DoubleList zps$getZs();

    @Invoker("<init>")
    static ArrayVoxelShape zps$create(DiscreteVoxelShape shape, DoubleList xs, DoubleList ys, DoubleList zs) {
        throw new AssertionError("Replaced by Mixin");
    }
}
