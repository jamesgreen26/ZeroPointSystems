package g_mungus.zps.client.renderer.contraption;

import java.util.function.Consumer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.BlockModelBuilder;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import net.minecraft.core.Direction;

/**
 * GPU-instanced rendering of a Servo Motor's contraption while the Flywheel
 * backend is active. Builds one baked model from the captured blocks and
 * applies the live rotation each frame. Falls back to
 * {@link ServoMotorBlockEntityRenderer} when Flywheel is off.
 */
public class ServoMotorVisual extends AbstractBlockEntityVisual<ServoMotorBlockEntity>
	implements SimpleDynamicVisual {

	@org.jetbrains.annotations.Nullable
	private TransformedInstance structure;
	@org.jetbrains.annotations.Nullable
	private Contraption builtContraption;

	public ServoMotorVisual(VisualizationContext ctx, ServoMotorBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);
		setupStructure();
		if (structure != null)
			applyTransform(partialTick);
	}

	private void setupStructure() {
		Contraption contraption = blockEntity.getContraption();
		builtContraption = contraption;
		if (structure != null) {
			structure.delete();
			structure = null;
		}
		if (contraption == null || contraption.isEmpty())
			return;

		Model model = new BlockModelBuilder(
			new ContraptionRenderWorld(blockEntity.getLevel(), contraption),
			contraption.getBlocks().keySet()).build();

		structure = instancerProvider()
			.instancer(InstanceTypes.TRANSFORMED, model)
			.createInstance();
	}

	@Override
	public void beginFrame(DynamicVisual.Context ctx) {
		if (blockEntity.getContraption() != builtContraption)
			setupStructure();
		if (structure != null)
			applyTransform(ctx.partialTick());
	}

	private void applyTransform(float partialTick) {
		float angle = blockEntity.getInterpolatedAngle(partialTick);
		Direction facing = blockEntity.getFacing();

		var transform = structure.setIdentityTransform()
			.translate(getVisualPosition())
			.translate(facing.getStepX(), facing.getStepY(), facing.getStepZ())
			.translate(0.5f, 0.5f, 0.5f);

		switch (blockEntity.getRotationAxis()) {
			case X -> transform.rotateXDegrees(angle);
			case Y -> transform.rotateYDegrees(angle);
			case Z -> transform.rotateZDegrees(angle);
		}

		transform.translate(-0.5f, -0.5f, -0.5f).setChanged();
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
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		if (structure != null)
			consumer.accept(structure);
	}
}
