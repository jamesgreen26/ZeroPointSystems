package g_mungus.zps.room;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A flame, soul ones included, and what it needs of the gas around it; see {@link Flames}. Every
 * flame needs the gas breathable as an entity does ({@link g_mungus.zps.gas.ModGasTags#MIN_BREATHABLE_SHARE}),
 * and goes out where it is not, leaving the gas as it is. Fire, campfires and candles are built in;
 * the rest, torches and lanterns among them, are declared by datapacks ({@link Extinguishing}).
 */
public enum Burner {
    CAMPFIRE,
    FIRE,
    /** Lit candles, and candles on a cake. */
    CANDLE,
    /** A block a datapack names in {@link Extinguishing}, going out into the state it gives. */
    CONVERTED;

    /**
     * The flame {@code state} is, or null if it is not one; an unlit campfire or candle is not. The
     * built-in flames come before the datapacks' ones.
     */
    public static @Nullable Burner of(BlockState state) {
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            return CAMPFIRE;
        }
        if (state.getBlock() instanceof BaseFireBlock) {
            return FIRE;
        }
        if (state.getBlock() instanceof AbstractCandleBlock && state.getValue(AbstractCandleBlock.LIT)) {
            return CANDLE;
        }
        if (Extinguishing.isFlame(state)) {
            return CONVERTED;
        }
        return null;
    }

    /**
     * Put out the flame at {@code pos} for want of air: a datapack's flame becomes what it names, a
     * campfire is doused, fire is gone, a candle is snuffed as a player snuffs it.
     */
    public void extinguish(ServerLevel level, BlockPos pos, BlockState state) {
        if (this == CANDLE) {
            AbstractCandleBlock.extinguish(null, state, level, pos);
            return;
        }
        level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
        switch (this) {
            case CONVERTED -> {
                BlockState out = Extinguishing.extinguished(state);
                if (out != null) {
                    level.setBlockAndUpdate(pos, out);
                }
            }
            case CAMPFIRE -> {
                CampfireBlock.dowse(null, level, pos, state);
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
            }
            case FIRE -> level.removeBlock(pos, false);
            case CANDLE -> {
            }
        }
    }
}
