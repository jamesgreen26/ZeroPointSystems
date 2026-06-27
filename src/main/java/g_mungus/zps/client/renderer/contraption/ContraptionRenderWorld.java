package g_mungus.zps.client.renderer.contraption;

import javax.annotation.Nullable;

import g_mungus.zps.contraption.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A minimal {@link net.minecraft.world.level.BlockAndTintGetter} that exposes a
 * contraption's captured blocks at their LOCAL positions, while delegating
 * shading/tint/light to the real client level. Flywheel re-lights the baked
 * instance after the fact, so approximate lighting here is fine.
 */
public class ContraptionRenderWorld implements net.minecraft.world.level.BlockAndTintGetter {

	private final Level level;
	private final Contraption contraption;

	public ContraptionRenderWorld(Level level, Contraption contraption) {
		this.level = level;
		this.contraption = contraption;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		StructureBlockInfo info = contraption.getBlocks().get(pos);
		return info == null ? Blocks.AIR.defaultBlockState() : info.state();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public float getShade(Direction direction, boolean shade) {
		return level.getShade(direction, shade);
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return level.getLightEngine();
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
		return level.getBlockTint(pos, colorResolver);
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinBuildHeight() {
		return level.getMinBuildHeight();
	}
}
