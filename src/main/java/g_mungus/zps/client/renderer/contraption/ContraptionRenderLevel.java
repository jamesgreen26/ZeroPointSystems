package g_mungus.zps.client.renderer.contraption;

import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.engine_room.flywheel.api.visualization.VisualizationLevel;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionHostLevel;
import g_mungus.zps.contraption.ContraptionSimLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side host {@link Level} for a contraption's live render block entities. Extends
 * {@link ContraptionSimLevel} (which already overlays the contraption's blocks at their LOCAL
 * positions) and additionally:
 *
 * <ul>
 *   <li>returns the reconstructed block entity at a LOCAL position from {@link #getBlockEntity}
 *       (the base returns {@code null}), so neighbour-aware renderers/tickers — double chests,
 *       conduit frames — resolve against the contraption's own block entities;</li>
 *   <li>routes particle spawns through the contraption transform so smoke/flame appear on the
 *       moving structure instead of at the local origin near world 0,0,0.</li>
 * </ul>
 *
 * <p>Owned by {@link ContraptionRenderState}; the {@code blockEntities} map and the transform
 * supplier are the render state's live ones. The wrapped real client level (via
 * {@link #getLevel()}) is where transformed particles are actually emitted. This level reports
 * {@code isClientSide == true} (it copies the wrapped client level's flag), so block tickers
 * resolved against it are the client variants.
 *
 * <p>Implements Flywheel's {@link VisualizationLevel} marker so its hosted block entities are
 * reported as visualizable — otherwise Flywheel only considers {@code Minecraft#level} supported and
 * would disable the Flywheel visual path / {@code skipVanillaRender} for contraption block entities.
 */
public class ContraptionRenderLevel extends ContraptionSimLevel implements VisualizationLevel, ContraptionHostLevel {

	private final Map<BlockPos, BlockEntity> blockEntities;
	@Nullable
	private final Supplier<ContraptionTransform> transform;
	/** The motor whose contraption this level hosts, so a nested motor can compose its transform. */
	@Nullable
	private final ServoMotorBlockEntity hostMotor;

	public ContraptionRenderLevel(Level level, Contraption contraption,
		Map<BlockPos, BlockEntity> blockEntities, @Nullable Supplier<ContraptionTransform> transform,
		@Nullable ServoMotorBlockEntity hostMotor) {
		super(level, contraption);
		this.blockEntities = blockEntities;
		this.transform = transform;
		this.hostMotor = hostMotor;
	}

	@Nullable
	@Override
	public ServoMotorBlockEntity getContraptionHostMotor() {
		return hostMotor;
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return blockEntities.get(pos);
	}

	@Override
	public void addParticle(ParticleOptions options, double x, double y, double z, double xs, double ys, double zs) {
		forwardParticle(options, x, y, z, xs, ys, zs);
	}

	@Override
	public void addParticle(ParticleOptions options, boolean force, double x, double y, double z, double xs, double ys,
		double zs) {
		forwardParticle(options, x, y, z, xs, ys, zs);
	}

	@Override
	public void addAlwaysVisibleParticle(ParticleOptions options, double x, double y, double z, double xs, double ys,
		double zs) {
		forwardParticle(options, x, y, z, xs, ys, zs);
	}

	@Override
	public void addAlwaysVisibleParticle(ParticleOptions options, boolean ignoreRange, double x, double y, double z,
		double xs, double ys, double zs) {
		forwardParticle(options, x, y, z, xs, ys, zs);
	}

	/** Emit a particle (given in contraption-local space) onto the real level at its rotated world pose. */
	private void forwardParticle(ParticleOptions options, double x, double y, double z, double xs, double ys,
		double zs) {
		if (transform == null) {
			getLevel().addParticle(options, x, y, z, xs, ys, zs);
			return;
		}
		ContraptionTransform t = transform.get();
		Vec3 world = t.localToWorld(new Vec3(x, y, z));
		Vec3 vel = t.localDirToWorld(new Vec3(xs, ys, zs));
		getLevel().addParticle(options, world.x, world.y, world.z, vel.x, vel.y, vel.z);
	}
}
