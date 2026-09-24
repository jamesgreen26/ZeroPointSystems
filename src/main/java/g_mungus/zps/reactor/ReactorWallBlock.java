package g_mungus.zps.reactor;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A block that can form part of a reactor shell. Placing or removing one may seal a cavity or
 * open one, so both report to the {@link ReactorManager}.
 *
 * <p>Membership is decided by the {@link #REACTOR_WALL} tag rather than this interface so a
 * datapack can extend the set; the interface just gives the blocks their hooks.
 */
public interface ReactorWallBlock {

    TagKey<Block> REACTOR_WALL = TagKey.create(Registries.BLOCK, ZPSMod.resource("reactor_wall"));

    static void onPlaced(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ReactorManager.get(serverLevel).onWallBlockChanged(serverLevel, pos, false);
        }
    }

    static void onRemoved(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ReactorManager.get(serverLevel).onWallBlockChanged(serverLevel, pos, true);
        }
    }
}
