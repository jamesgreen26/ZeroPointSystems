package g_mungus.zps.networking;

import g_mungus.zps.mixin.ArrayVoxelShapeAccessor;
import g_mungus.zps.mixin.BitSetDiscreteVoxelShapeAccessor;
import g_mungus.zps.mixin.VoxelShapeAccessor;
import net.minecraft.network.FriendlyByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
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
public final class VoxelShapeStreamCodec {

    public static final VoxelShapeStreamCodec INSTANCE = new VoxelShapeStreamCodec();

    /** Larger than any shape worth sending; a bad packet stops here rather than in an allocation. */
    private static final int MAX_AXIS = 4096;

    private VoxelShapeStreamCodec() {
    }

    public void encode(FriendlyByteBuf buffer, VoxelShape shape) {
        if (shape.isEmpty()) {
            buffer.writeVarInt(0);
            return;
        }
        if (!(shape instanceof ArrayVoxelShape) || !(((VoxelShapeAccessor) shape).zps$getShape() instanceof BitSetDiscreteVoxelShape)) {
            shape = Shapes.joinUnoptimized(Shapes.empty(), shape, BooleanOp.OR);
        }
        ArrayVoxelShapeAccessor array = (ArrayVoxelShapeAccessor) shape;
        DiscreteVoxelShape grid = ((VoxelShapeAccessor) shape).zps$getShape();
        BitSetDiscreteVoxelShapeAccessor bits = (BitSetDiscreteVoxelShapeAccessor) grid;

        buffer.writeVarInt(grid.getSize(Direction.Axis.X));
        buffer.writeVarInt(grid.getSize(Direction.Axis.Y));
        buffer.writeVarInt(grid.getSize(Direction.Axis.Z));
        writeCoords(buffer, array.zps$getXs());
        writeCoords(buffer, array.zps$getYs());
        writeCoords(buffer, array.zps$getZs());
        buffer.writeVarInt(bits.zps$getXMin());
        buffer.writeVarInt(bits.zps$getYMin());
        buffer.writeVarInt(bits.zps$getZMin());
        buffer.writeVarInt(bits.zps$getXMax());
        buffer.writeVarInt(bits.zps$getYMax());
        buffer.writeVarInt(bits.zps$getZMax());
        buffer.writeByteArray(bits.zps$getStorage().toByteArray());
    }

    public VoxelShape decode(FriendlyByteBuf buffer) {
        int sizeX = buffer.readVarInt();
        if (sizeX == 0) {
            return Shapes.empty();
        }
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        if (sizeX < 0 || sizeY < 1 || sizeZ < 1 || sizeX > MAX_AXIS || sizeY > MAX_AXIS || sizeZ > MAX_AXIS) {
            throw new IllegalArgumentException("Bad shape size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        DoubleList xs = readCoords(buffer, sizeX + 1);
        DoubleList ys = readCoords(buffer, sizeY + 1);
        DoubleList zs = readCoords(buffer, sizeZ + 1);
        int xMin = buffer.readVarInt();
        int yMin = buffer.readVarInt();
        int zMin = buffer.readVarInt();
        int xMax = buffer.readVarInt();
        int yMax = buffer.readVarInt();
        int zMax = buffer.readVarInt();
        BitSet storage = BitSet.valueOf(buffer.readByteArray());
        if (storage.length() > sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Shape storage larger than its grid");
        }

        BitSetDiscreteVoxelShape grid = BitSetDiscreteVoxelShape.withFilledBounds(sizeX, sizeY, sizeZ,
                xMin, yMin, zMin, xMax, yMax, zMax);
        ((BitSetDiscreteVoxelShapeAccessor) (Object) grid).zps$setStorage(storage);
        return ArrayVoxelShapeAccessor.zps$create(grid, xs, ys, zs);
    }

    private static void writeCoords(FriendlyByteBuf buffer, DoubleList coords) {
        for (int i = 0; i < coords.size(); i++) {
            buffer.writeDouble(coords.getDouble(i));
        }
    }

    private static DoubleList readCoords(FriendlyByteBuf buffer, int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = buffer.readDouble();
        }
        return DoubleArrayList.wrap(values);
    }
}
