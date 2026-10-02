package g_mungus.zps.mixin;

import g_mungus.zps.block.BrushablePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every {@link BrushableBlock} — vanilla suspicious sand and gravel, ZPS suspicious red sand and
 * other mods' suspicious blocks — survives falling and piston movement with its
 * {@link BrushableBlockEntity} payload intact.
 *
 * <p>Vanilla annihilates these blocks in both cases: {@code BrushableBlock#tick} calls
 * {@code FallingBlockEntity#disableDrop()}, and the block properties carry
 * {@link PushReaction#DESTROY}. Since their loot table is empty, nothing drops either way.
 *
 * <p>The piston half of the behaviour also needs {@code PistonBaseBlockMixin} and
 * {@code PistonMovingBlockEntityMixin}, both of which gate on this type. Subclasses that override
 * these methods themselves keep their own behaviour.
 */
@Mixin(BrushableBlock.class)
public abstract class BrushableBlockMixin {

    /**
     * Overrides NeoForge's {@code IBlockExtension#getPistonPushReaction} default. NeoForge evaluates
     * it per call from {@code BlockStateBase#getPistonPushReaction} rather than baking it into the
     * cached state, so returning a non-null value here cleanly overrides the
     * {@code .pushReaction(DESTROY)} in the block properties.
     */
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.NORMAL;
    }

    /**
     * {@code BrushableBlock#tick} minus the {@code disableDrop()}, plus a block entity snapshot so
     * the buried loot rides along. Cancels the original, which would spawn a second
     * {@link FallingBlockEntity}.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void zps$fallWithPayload(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                     CallbackInfo ci) {
        ci.cancel();

        if (level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable) {
            brushable.checkReset();
        }

        if (!FallingBlock.isFree(level.getBlockState(pos.below())) || pos.getY() < level.getMinBuildHeight()) {
            return;
        }

        // checkReset() may have rewritten DUSTED, or replaced us outright once brushing completed.
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof BrushableBlock)) {
            return;
        }

        // Must read the block entity before fall(): it does setBlock(pos, air) internally.
        CompoundTag payload = BrushablePayload.snapshot(level, pos);

        // brushCount lives only on the block entity and is not serialised, so a non-zero DUSTED
        // would arrive at the landing site with nothing backing it.
        BlockState fallingState = current.hasProperty(BlockStateProperties.DUSTED)
                ? current.setValue(BlockStateProperties.DUSTED, 0)
                : current;

        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, fallingState);
        // Public field, only read when the entity lands, so assigning after fall() is fine.
        falling.blockData = payload;
    }

    /**
     * Vanilla plays destroy particles and a {@code BLOCK_DESTROY} game event here, which is what
     * makes a failed landing look like the block was smashed. Regular sand inherits {@code
     * Fallable}'s empty default and simply drops its item; match sand.
     *
     * <p>The drop itself is swapped for {@code getTurnsInto()} by {@code FallingBlockEntityMixin},
     * since the buried payload is already lost by the time a landing fails.
     */
    @Inject(method = "onBrokenAfterFall", at = @At("HEAD"), cancellable = true)
    private void zps$breakLikeSand(Level level, BlockPos pos, FallingBlockEntity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
