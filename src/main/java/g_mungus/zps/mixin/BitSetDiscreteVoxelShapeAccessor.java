package g_mungus.zps.mixin;

import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.BitSet;

/** The storage and filled bounds of a {@link BitSetDiscreteVoxelShape}, for serialising it exactly. */
@Mixin(BitSetDiscreteVoxelShape.class)
public interface BitSetDiscreteVoxelShapeAccessor {

    @Accessor("storage")
    BitSet zps$getStorage();

    @Mutable
    @Accessor("storage")
    void zps$setStorage(BitSet storage);

    @Accessor("xMin")
    int zps$getXMin();

    @Accessor("yMin")
    int zps$getYMin();

    @Accessor("zMin")
    int zps$getZMin();

    @Accessor("xMax")
    int zps$getXMax();

    @Accessor("yMax")
    int zps$getYMax();

    @Accessor("zMax")
    int zps$getZMax();
}
