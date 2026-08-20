package g_mungus.zps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla suspicious sand / suspicious gravel, except the block survives falling and piston
 * movement with its {@link BrushableBlockEntity} payload intact.
 *
 * <p>Vanilla annihilates these blocks in both cases: {@code BrushableBlock#tick} calls
 * {@code FallingBlockEntity#disableDrop()}, and the block properties carry
 * {@link PushReaction#DESTROY}. Since their loot table is empty, nothing drops either way.
 *
 * <p>Swapped in for the vanilla instances by {@code g_mungus.zps.mixin.BlocksMixin}. The piston
 * half of the behaviour also needs {@code PistonBaseBlockMixin} and
 * {@code PistonMovingBlockEntityMixin}, both of which gate on this type.
 *
 * <p><b>This class must never gain a static initialiser.</b> Instances are constructed from inside
 * {@code Blocks.<clinit>}, and Java class initialisation is re-entrant on the same thread — a
 * {@code static final} referencing {@code Blocks}, {@code ModBlocks} or {@code Items} would
 * silently read {@code null} out of a half-initialised class rather than fail loudly.
 */
public class ZPSBrushableBlock extends BrushableBlock {

    public ZPSBrushableBlock(Block turnsInto, SoundEvent brushSound, SoundEvent brushCompletedSound,
                             BlockBehaviour.Properties properties) {
        super(turnsInto, brushSound, brushCompletedSound, properties);
    }

    /**
     * NeoForge evaluates this per call from {@code BlockStateBase#getPistonPushReaction} rather
     * than baking it into the cached state, so returning a non-null value here cleanly overrides
     * the {@code .pushReaction(DESTROY)} in the vanilla block properties. No mixin needed.
     */
    @Override
    public PushReaction getPistonPushReaction(@NotNull BlockState state) {
        return PushReaction.NORMAL;
    }

    /**
     * {@code BrushableBlock#tick} minus the {@code disableDrop()}, plus a block entity snapshot so
     * the buried loot rides along. Deliberately does not call {@code super} — that would spawn a
     * second {@link FallingBlockEntity}.
     */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                     @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable) {
            brushable.checkReset();
        }

        if (!FallingBlock.isFree(level.getBlockState(pos.below())) || pos.getY() < level.getMinBuildHeight()) {
            return;
        }

        // checkReset() may have rewritten DUSTED, or replaced us outright once brushing completed.
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof ZPSBrushableBlock)) {
            return;
        }

        // Must read the block entity before fall(): it does setBlock(pos, air) internally.
        CompoundTag payload = snapshot(level, pos);

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
     */
    @Override
    public void onBrokenAfterFall(@NotNull Level level, @NotNull BlockPos pos,
                                  @NotNull FallingBlockEntity entity) {
    }

    /** Serialises a brushable block entity's payload, or returns null if there is nothing to carry. */
    @Nullable
    public static CompoundTag snapshot(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable
                ? brushable.saveWithoutMetadata(level.registryAccess())
                : null;
    }

    /**
     * Applies a payload from {@link #snapshot}, mirroring the sequence {@link FallingBlockEntity}
     * uses when a falling block lands on top of a block entity.
     */
    public static void restore(Level level, BlockPos pos, @Nullable CompoundTag payload) {
        if (payload == null || level.isClientSide) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BrushableBlockEntity)) {
            return;
        }

        CompoundTag merged = blockEntity.saveWithoutMetadata(level.registryAccess());
        for (String key : payload.getAllKeys()) {
            merged.put(key, payload.get(key).copy());
        }

        try {
            blockEntity.loadWithComponents(merged, level.registryAccess());
        } catch (Exception e) {
            return;
        }

        blockEntity.setChanged();
    }
}
