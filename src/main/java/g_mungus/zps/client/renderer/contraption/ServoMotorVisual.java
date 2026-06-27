package g_mungus.zps.client.renderer.contraption;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.math.Axis;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.BlockModelBuilder;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * GPU-instanced rendering of a Servo Motor's contraption while the Flywheel
 * backend is active. Builds one baked model from the captured blocks and applies
 * the live rotation each frame. Captured block entities that have their own
 * Flywheel visualizer and opt out of vanilla rendering are hosted as child
 * visuals on a {@link VisualEmbedding} carrying the contraption transform; the
 * remaining block entities are drawn by {@link ServoMotorBlockEntityRenderer}.
 */
public class ServoMotorVisual extends AbstractBlockEntityVisual<ServoMotorBlockEntity>
	implements SimpleDynamicVisual {

	@org.jetbrains.annotations.Nullable
	private TransformedInstance structure;
	@org.jetbrains.annotations.Nullable
	private Contraption builtContraption;

	private final VisualEmbedding embedding;
	private final List<BlockEntityVisual<?>> children = new ArrayList<>();

	public ServoMotorVisual(VisualizationContext ctx, ServoMotorBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);
		embedding = visualizationContext.createEmbedding(Vec3i.ZERO);
		setupStructure(partialTick);
		applyTransform(partialTick);
	}

	private void setupStructure(float partialTick) {
		Contraption contraption = blockEntity.getContraption();
		builtContraption = contraption;

		if (structure != null) {
			structure.delete();
			structure = null;
		}
		children.forEach(BlockEntityVisual::delete);
		children.clear();

		if (contraption == null || contraption.isEmpty())
			return;

		Model model = new BlockModelBuilder(
			new ContraptionRenderWorld(blockEntity.getLevel(), contraption),
			contraption.getBlocks().keySet()).build();

		structure = instancerProvider()
			.instancer(InstanceTypes.TRANSFORMED, model)
			.createInstance();

		setupChildren(partialTick);
	}

	@SuppressWarnings("unchecked")
	private void setupChildren(float partialTick) {
		ContraptionRenderState renderState = blockEntity.getRenderState();
		if (renderState == null)
			return;

		// Mirror Create: create a Flywheel child visual for every captured block entity
		// that has a visualizer. The BER independently decides whether to ALSO run the
		// vanilla renderer (it skips a block entity only when it opts out of vanilla
		// render). This lets e.g. the robotic arm draw its segments via the child visual
		// while its BER still draws the held item.
		for (BlockEntity be : renderState.getBlockEntities()) {
			BlockEntityVisualizer<? super BlockEntity> visualizer =
				(BlockEntityVisualizer<? super BlockEntity>) VisualizerRegistry.getVisualizer(be.getType());
			if (visualizer == null)
				continue;
			children.add(visualizer.createVisual(embedding, be, partialTick));
		}
	}

	@Override
	public void beginFrame(DynamicVisual.Context ctx) {
		if (blockEntity.getContraption() != builtContraption)
			setupStructure(ctx.partialTick());
		applyTransform(ctx.partialTick());
	}

	private void applyTransform(float partialTick) {
		// Drive the embedding (and thus all child block-entity visuals) with the
		// contraption transform, and mirror it onto the structure instance.
		Matrix4f pose = contraptionPose(partialTick);
		embedding.transforms(pose, pose.normal(new Matrix3f()));

		if (structure == null)
			return;
		structure.setTransform(pose).setChanged();
	}

	private Matrix4f contraptionPose(float partialTick) {
		float angle = blockEntity.getInterpolatedAngle(partialTick);
		Direction facing = blockEntity.getFacing();
		BlockPos vp = getVisualPosition();

		Matrix4f pose = new Matrix4f();
		pose.translate(vp.getX() + facing.getStepX(), vp.getY() + facing.getStepY(), vp.getZ() + facing.getStepZ());
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.rotate(axisRotation(facing.getAxis(), angle));
		pose.translate(-0.5f, -0.5f, -0.5f);
		return pose;
	}

	private static org.joml.Quaternionf axisRotation(Direction.Axis axis, float degrees) {
		return switch (axis) {
			case X -> Axis.XP.rotationDegrees(degrees);
			case Y -> Axis.YP.rotationDegrees(degrees);
			case Z -> Axis.ZP.rotationDegrees(degrees);
		};
	}

	@Override
	public void updateLight(float partialTick) {
		if (structure != null)
			relight(structure);
	}

	@Override
	protected void _delete() {
		if (structure != null) {
			structure.delete();
			structure = null;
		}
		children.forEach(BlockEntityVisual::delete);
		children.clear();
		embedding.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		if (structure != null)
			consumer.accept(structure);
	}
}
