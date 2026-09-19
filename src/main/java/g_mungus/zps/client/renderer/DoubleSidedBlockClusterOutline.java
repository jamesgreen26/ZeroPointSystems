package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.BlockClusterOutline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A block cluster outline whose faces are visible from both sides.
 * <p>
 * Catnip's cluster outline always renders its faces with backface culling enabled
 * (its {@code disableCull} param is only honoured by the AABB outlines), so a volume
 * the player stands inside of is invisible. Every face is buffered a second time with
 * reversed winding and an inverted normal, which shows the inner side of the volume.
 */
public class DoubleSidedBlockClusterOutline extends BlockClusterOutline {
    private final Vector3f innerPos0 = new Vector3f();
    private final Vector3f innerPos1 = new Vector3f();
    private final Vector3f innerPos2 = new Vector3f();
    private final Vector3f innerPos3 = new Vector3f();
    private final Vector3f innerNormal = new Vector3f();

    public DoubleSidedBlockClusterOutline(Iterable<BlockPos> positions) {
        super(positions);
    }

    @Override
    protected void bufferBlockFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Direction face,
                                   Vector4f color, int lightmap) {
        super.bufferBlockFace(pose, consumer, pos, face, color, lightmap);

        loadFaceData(face, innerPos0, innerPos1, innerPos2, innerPos3, innerNormal);
        addPos(pos.getX() + face.getStepX() / 128f,
                pos.getY() + face.getStepY() / 128f,
                pos.getZ() + face.getStepZ() / 128f,
                innerPos0, innerPos1, innerPos2, innerPos3);
        innerNormal.mul(-1.0f);

        bufferQuad(pose, consumer, innerPos3, innerPos2, innerPos1, innerPos0, color, lightmap, innerNormal);
    }
}
