package g_mungus.zps.block;

import g_mungus.zps.multiblock.MultiblockBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Block item for the Beam Collector. Keeps {@link MultiblockBlockItem}'s stripping of stale structure data, but not
 * its layer filling: a panel is one block deep, so "the next layer" of an upward-facing panel is the space in
 * front of its mouth.
 */
public class BeamCollectorBlockItem extends MultiblockBlockItem {

    public BeamCollectorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected void tryMultiPlace(BlockPlaceContext ctx) {
    }
}
