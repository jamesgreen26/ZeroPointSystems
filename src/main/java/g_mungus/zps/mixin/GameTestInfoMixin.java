package g_mungus.zps.mixin;

import g_mungus.zps.gametest.SkyAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Encases each gametest arena in barriers, as 1.21's framework does: walls all round and a roof,
 * unless the test is marked {@link SkyAccess}. ZPS's tests are written against that, and without
 * it they reach into each other on 1.20.1 — beams pulling items, launched blocks landing, water
 * flowing in. The next batch's clearing takes the barriers away again.
 */
@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {

    @Inject(method = "startExecution", at = @At("HEAD"))
    private void zps$encaseArena(CallbackInfo ci) {
        GameTestInfo self = (GameTestInfo) (Object) this;
        ServerLevel level = self.getLevel();
        AABB bounds = self.getStructureBounds();
        boolean roof = !SkyAccess.TESTS.contains(self.getTestName());
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, 0, -1);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos at : BlockPos.betweenClosed(min, max)) {
            boolean wall = at.getX() == min.getX() || at.getX() == max.getX()
                    || at.getZ() == min.getZ() || at.getZ() == max.getZ();
            boolean top = at.getY() == max.getY();
            if (wall || (top && roof)) {
                level.setBlockAndUpdate(at, Blocks.BARRIER.defaultBlockState());
            }
        }
    }
}
