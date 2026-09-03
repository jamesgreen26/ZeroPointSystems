package g_mungus.zps.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.zps.block.SieveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a block that collides differently per entity keep doing so inside a sub-level.
 *
 * <p>Sable asks each block for its shape through the two-argument
 * {@code BlockState#getCollisionShape}, which reads the shape cached with an empty context, so the
 * entity it is moving never reaches the block. It has one exception, hardcoded for
 * {@code ScaffoldingBlock}. Without a second one the sieve presents the same mesh to everything, and
 * items and falling blocks that should drop through it to be sifted land on top of it instead.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision", remap = false)
public class SubLevelEntityCollisionMixin {

    @WrapOperation(
            method = "getSubLevelEntityCollisionShape",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape"
                            + "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
            ),
            require = 0
    )
    private static VoxelShape zps$collisionShapeForEntity(BlockState state, BlockGetter level, BlockPos pos,
                                                          Operation<VoxelShape> original,
                                                          @Local(argsOnly = true) Entity entity) {
        if (state.getBlock() instanceof SieveBlock) {
            return state.getCollisionShape(level, pos, CollisionContext.of(entity));
        }

        return original.call(state, level, pos);
    }
}
