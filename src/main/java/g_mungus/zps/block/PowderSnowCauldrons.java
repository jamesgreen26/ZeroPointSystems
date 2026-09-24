package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/**
 * The one rule for putting powder snow into a cauldron a layer at a time: an empty cauldron
 * becomes a powder snow cauldron at its lowest level, a powder snow cauldron gains a level until
 * full, and anything else is left alone. Shared by the dripstone drip and the snow golem trail.
 */
public final class PowderSnowCauldrons {

    private PowderSnowCauldrons() {
    }

    /** Whether {@link #addLayer} would change this block. */
    public static boolean canAddLayer(BlockState state) {
        return state.is(Blocks.CAULDRON)
                || state.is(Blocks.POWDER_SNOW_CAULDRON)
                && state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL;
    }

    /** Adds one layer, emitting the block-change game event. Returns the new state, or null if nothing changed. */
    @Nullable
    public static BlockState addLayer(Level level, BlockPos pos, BlockState state) {
        if (!canAddLayer(state)) {
            return null;
        }
        BlockState filled = state.is(Blocks.CAULDRON)
                ? Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                : state.cycle(LayeredCauldronBlock.LEVEL);
        level.setBlockAndUpdate(pos, filled);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(filled));
        return filled;
    }

    /**
     * A snow golem's trail, for cauldrons. Vanilla samples the same four footprint positions and
     * drops a snow layer on each that is air; a cauldron at a footprint (the golem has fallen into
     * the basin) or directly under one (it is standing on the rim) gets a layer of powder snow
     * instead. One layer per tick, so a passing golem tops a cauldron up rather than filling it
     * in a single step.
     */
    public static void snowGolemTrail(Mob golem) {
        Level level = golem.level();
        if (level.isClientSide || !ForgeEventFactory.getMobGriefingEvent(level, golem)) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            int x = Mth.floor(golem.getX() + (i % 2 * 2 - 1) * 0.25F);
            int y = Mth.floor(golem.getY());
            int z = Mth.floor(golem.getZ() + (i / 2 % 2 * 2 - 1) * 0.25F);
            BlockPos pos = cauldronUnderfoot(level, new BlockPos(x, y, z));
            if (pos != null && addLayer(level, pos, level.getBlockState(pos)) != null) {
                return;
            }
        }
    }

    @Nullable
    private static BlockPos cauldronUnderfoot(Level level, BlockPos footprint) {
        BlockState at = footprint.getY() >= level.getMinBuildHeight() ? level.getBlockState(footprint) : Blocks.AIR.defaultBlockState();
        if (canAddLayer(at)) {
            return footprint;
        }
        if (at.isAir() && canAddLayer(level.getBlockState(footprint.below()))) {
            return footprint.below();
        }
        return null;
    }
}
