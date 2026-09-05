package g_mungus.zps.networking;

import g_mungus.zps.mixin.ArrayVoxelShapeAccessor;
import g_mungus.zps.mixin.BitSetDiscreteVoxelShapeAccessor;
import g_mungus.zps.mixin.VoxelShapeAccessor;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.BitSet;

/**
 * Sends a {@link VoxelShape} exactly as vanilla holds it: the three coordinate lists and the
 * bit grid with its filled bounds. Vanilla has no codec of its own for shapes.
 *
 * <p>Only {@link ArrayVoxelShape} over a {@link BitSetDiscreteVoxelShape} can be taken apart;
 * anything else is first joined with the empty shape, which is how vanilla itself turns any shape
 * into that form.
 */
public final class VoxelShapeStreamCodec implements StreamCodec<ByteBuf, VoxelShape> {

    public static final VoxelShapeStreamCodec INSTANCE = new VoxelShapeStreamCodec();

    /** Larger than any shape worth sending; a bad packet stops here rather than in an allocation. */
    private static final int MAX_AXIS = 4096;

    private VoxelShapeStreamCodec() {
    }

    @Override
    public void encode(ByteBuf buffer, VoxelShape shape) {
        if (shape.isEmpty()) {
            ByteBufCodecs.VAR_INT.encode(buffer, 0);
            return;
        }
        if (!(shape instanceof ArrayVoxelShape) || !(((VoxelShapeAccessor) shape).zps$getShape() instanceof BitSetDiscreteVoxelShape)) {
            shape = Shapes.joinUnoptimized(Shapes.empty(), shape, BooleanOp.OR);
        }
        ArrayVoxelShapeAccessor array = (ArrayVoxelShapeAccessor) shape;
        DiscreteVoxelShape grid = ((VoxelShapeAccessor) shape).zps$getShape();
        BitSetDiscreteVoxelShapeAccessor bits = (BitSetDiscreteVoxelShapeAccessor) grid;

        ByteBufCodecs.VAR_INT.encode(buffer, grid.getSize(Direction.Axis.X));
        ByteBufCodecs.VAR_INT.encode(buffer, grid.getSize(Direction.Axis.Y));
        ByteBufCodecs.VAR_INT.encode(buffer, grid.getSize(Direction.Axis.Z));
        writeCoords(buffer, array.zps$getXs());
        writeCoords(buffer, array.zps$getYs());
        writeCoords(buffer, array.zps$getZs());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getXMin());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getYMin());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getZMin());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getXMax());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getYMax());
        ByteBufCodecs.VAR_INT.encode(buffer, bits.zps$getZMax());
        ByteBufCodecs.BYTE_ARRAY.encode(buffer, bits.zps$getStorage().toByteArray());
    }

    @Override
    public VoxelShape decode(ByteBuf buffer) {
        int sizeX = ByteBufCodecs.VAR_INT.decode(buffer);
        if (sizeX == 0) {
            return Shapes.empty();
        }
        int sizeY = ByteBufCodecs.VAR_INT.decode(buffer);
        int sizeZ = ByteBufCodecs.VAR_INT.decode(buffer);
        if (sizeX < 0 || sizeY < 1 || sizeZ < 1 || sizeX > MAX_AXIS || sizeY > MAX_AXIS || sizeZ > MAX_AXIS) {
            throw new IllegalArgumentException("Bad shape size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        DoubleList xs = readCoords(buffer, sizeX + 1);
        DoubleList ys = readCoords(buffer, sizeY + 1);
        DoubleList zs = readCoords(buffer, sizeZ + 1);
        int xMin = ByteBufCodecs.VAR_INT.decode(buffer);
        int yMin = ByteBufCodecs.VAR_INT.decode(buffer);
        int zMin = ByteBufCodecs.VAR_INT.decode(buffer);
        int xMax = ByteBufCodecs.VAR_INT.decode(buffer);
        int yMax = ByteBufCodecs.VAR_INT.decode(buffer);
        int zMax = ByteBufCodecs.VAR_INT.decode(buffer);
        BitSet storage = BitSet.valueOf(ByteBufCodecs.BYTE_ARRAY.decode(buffer));
        if (storage.length() > sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Shape storage larger than its grid");
        }

        BitSetDiscreteVoxelShape grid = BitSetDiscreteVoxelShape.withFilledBounds(sizeX, sizeY, sizeZ,
                xMin, yMin, zMin, xMax, yMax, zMax);
        ((BitSetDiscreteVoxelShapeAccessor) (Object) grid).zps$setStorage(storage);
        return ArrayVoxelShapeAccessor.zps$create(grid, xs, ys, zs);
    }

    private static void writeCoords(ByteBuf buffer, DoubleList coords) {
        for (int i = 0; i < coords.size(); i++) {
            buffer.writeDouble(coords.getDouble(i));
        }
    }

    private static DoubleList readCoords(ByteBuf buffer, int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = buffer.readDouble();
        }
        return DoubleArrayList.wrap(values);
    }
}
