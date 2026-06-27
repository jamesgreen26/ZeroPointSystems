package g_mungus.zps.client.renderer.contraption;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import g_mungus.zps.contraption.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Client-side cache of the block entities captured in a contraption, rebuilt
 * from their update tags. Mirrors Create's {@code ClientContraption} block-entity
 * reconstruction so that captured block entities keep rendering (chests, signs,
 * Flywheel-driven machines, etc.) while assembled.
 *
 * <p>The reconstructed block entities use the real client level (their block
 * positions are contraption-local), which is good enough for renderer logic;
 * lighting is approximated.
 */
public class ContraptionRenderState {

	private final Contraption contraption;
	private final List<BlockEntity> blockEntities = new ArrayList<>();

	public ContraptionRenderState(Level level, Contraption contraption) {
		this.contraption = contraption;
		for (StructureBlockInfo info : contraption.getBlocks().values()) {
			BlockEntity be = reconstruct(level, info.state(), info.pos(), contraption.getUpdateTags().get(info.pos()));
			if (be != null)
				blockEntities.add(be);
		}
	}

	/** The contraption this state was built for, so callers can detect changes. */
	public Contraption getContraption() {
		return contraption;
	}

	public List<BlockEntity> getBlockEntities() {
		return blockEntities;
	}

	@Nullable
	private static BlockEntity reconstruct(Level level, BlockState state, BlockPos localPos,
		@Nullable CompoundTag updateTag) {
		if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock))
			return null;
		BlockEntity be = entityBlock.newBlockEntity(localPos, state);
		if (be == null)
			return null;
		be.setLevel(level);
		be.setBlockState(state);
		if (updateTag != null)
			be.handleUpdateTag(updateTag, level.registryAccess());
		return be;
	}
}
