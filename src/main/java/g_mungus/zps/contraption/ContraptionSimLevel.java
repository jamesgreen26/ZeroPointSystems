package g_mungus.zps.contraption;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.createmod.catnip.levelWrappers.WrappedLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;

/**
 * A read-only {@link WrappedLevel} that overlays a contraption's captured blocks
 * at their LOCAL positions (air everywhere else), delegating everything else to
 * the wrapped real level. Used as the placement-simulation level so that vanilla
 * {@code getStateForPlacement}/{@code canSurvive} resolve states with the
 * contraption's own blocks as neighbours. Works on both sides (client prediction
 * and authoritative server placement) so they agree.
 */
public class ContraptionSimLevel extends WrappedLevel {

	private final Contraption contraption;

	public ContraptionSimLevel(Level level, Contraption contraption) {
		super(level);
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
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
		return predicate.test(getBlockState(pos));
	}
}
