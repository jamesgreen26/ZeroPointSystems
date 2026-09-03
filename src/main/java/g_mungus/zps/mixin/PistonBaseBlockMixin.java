package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import g_mungus.zps.block.MovedBlockEntityHolder;
import g_mungus.zps.block.ZPSBrushableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets {@link ZPSBrushableBlock} be moved by pistons, and snapshots its buried loot so the move
 * does not destroy it. Both halves are gated on the ZPS block type, so no other block-entity block
 * changes behaviour.
 *
 * <p>Push and sticky-pull share this code path: {@code triggerEvent} handles {@code
 * TRIGGER_CONTRACT} via {@code moveBlocks(level, pos, facing, false)}.
 */
@Mixin(PistonBaseBlock.class)
public class PistonBaseBlockMixin {

    /**
     * {@code isPushable} ends with {@code return !state.hasBlockEntity()}, which would otherwise
     * make a piston refuse to extend at all once our push reaction is NORMAL. Suppress that single
     * expression for our block only.
     *
     * <p>Deliberately not {@code @ModifyReturnValue}: this method has eight return sites (world
     * border, min/max build height, unbreakable, the BLOCK/DESTROY/PUSH_ONLY switch, extended
     * piston), and blanket-overriding them would let suspicious sand be pushed out of the world
     * border or past the build limit.
     */
    @ModifyExpressionValue(
            method = "isPushable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z"
            )
    )
    private static boolean zps$allowBrushableDespiteBlockEntity(
            boolean original,
            BlockState state,
            Level level,
            BlockPos pos,
            Direction moveDirection,
            boolean allowDestroy,
            Direction pistonDirection
    ) {
        if (original && state.getBlock() instanceof ZPSBrushableBlock) {
            return false;
        }
        return original;
    }

    /**
     * Snapshot every brushable payload in the structure before the piston mutates anything.
     *
     * <p>{@code getToPush()} is invoked exactly once in {@code moveBlocks}, right after
     * {@code resolve()} and before the first {@code setBlock}. Capturing here rather than inside
     * the per-block loop avoids depending on {@code PistonStructureResolver#reorderListAtCollision}
     * keeping the loop's farthest-block-first ordering valid for slime-branch structures.
     */
    @WrapOperation(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;"
            )
    )
    private List<BlockPos> zps$snapshotMovedLoot(
            PistonStructureResolver resolver,
            Operation<List<BlockPos>> original,
            @Local(argsOnly = true) Level level,
            @Share("zps$movedLoot") LocalRef<Map<BlockPos, CompoundTag>> shared
    ) {
        List<BlockPos> toPush = original.call(resolver);

        Map<BlockPos, CompoundTag> payloads = null;
        for (BlockPos pos : toPush) {
            if (!(level.getBlockState(pos).getBlock() instanceof ZPSBrushableBlock)) {
                continue;
            }
            CompoundTag payload = ZPSBrushableBlock.snapshot(level, pos);
            if (payload == null) {
                continue;
            }
            if (payloads == null) {
                payloads = new HashMap<>();
            }
            payloads.put(pos.immutable(), payload);
        }
        shared.set(payloads);

        return toPush;
    }

    /**
     * Hand the snapshot to the {@code PistonMovingBlockEntity} that will carry the block across the
     * stroke.
     *
     * <p>{@code moveBlocks} computes {@code direction = extending ? facing : facing.getOpposite()}
     * and then {@code destPos = sourcePos.relative(direction)}. Both {@code facing} and
     * {@code extending} are passed straight through to this call, so the source position is exactly
     * recoverable without reaching for a local.
     */
    @WrapOperation(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/MovingPistonBlock;newMovingBlockEntity("
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;ZZ)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
            ),
            expect = 2
    )
    private BlockEntity zps$attachMovedLoot(
            BlockPos destPos,
            BlockState movingPistonState,
            BlockState movedState,
            Direction facing,
            boolean extending,
            boolean isSourcePiston,
            Operation<BlockEntity> original,
            @Share("zps$movedLoot") LocalRef<Map<BlockPos, CompoundTag>> shared
    ) {
        BlockEntity blockEntity = original.call(
                destPos, movingPistonState, movedState, facing, extending, isSourcePiston);

        Map<BlockPos, CompoundTag> payloads = shared.get();
        if (payloads == null
                || isSourcePiston
                || !(movedState.getBlock() instanceof ZPSBrushableBlock)
                || !(blockEntity instanceof MovedBlockEntityHolder holder)) {
            return blockEntity;
        }

        Direction moveDirection = extending ? facing : facing.getOpposite();
        CompoundTag payload = payloads.get(destPos.relative(moveDirection.getOpposite()));
        if (payload != null) {
            holder.zps$setMovedBlockEntityTag(payload);
        }

        return blockEntity;
    }
}
