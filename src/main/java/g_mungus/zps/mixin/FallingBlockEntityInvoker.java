package g_mungus.zps.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the private {@code FallingBlockEntity(Level, double, double, double, BlockState)}
 * constructor.
 *
 * <p>The public route, {@code FallingBlockEntity#fall}, is unusable for spawning a replacement
 * mid-air: it calls {@code setBlock(pos, air)} on the position handed to it, which would delete
 * whatever block the entity is currently passing through.
 */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {

    @Invoker("<init>")
    static FallingBlockEntity zps$create(Level level, double x, double y, double z, BlockState state) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
