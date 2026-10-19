package g_mungus.zps.mixin.vs;

import g_mungus.zps.compat.GridPhysicsActor;
import g_mungus.zps.compat.VSGridPhysics;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Makes every {@link GridPhysicsActor} a Valkyrien Skies block entity physics listener. VS registers one as it
 * gets its ticker and calls it with no ship while it is in the world, which is passed over here.
 * <p>
 * An interface mixin cannot add a field, so the dimension VS hands over is not kept: it is read back from the
 * block entity's level, which VS only asks for once the block entity is in one.
 */
@Mixin(value = GridPhysicsActor.class, remap = false)
public interface PhysicsListenerExtension extends BlockEntityPhysicsListener {

    @Override
    default void physTick(PhysShip ship, PhysLevel level) {
        if (ship != null) {
            ((GridPhysicsActor) this).onGridPhysicsTick(new VSGridPhysics(ship));
        }
    }

    @Override
    default String getDimension() {
        Level level = ((BlockEntity) this).getLevel();
        return level == null ? null : VSGameUtilsKt.getDimensionId(level);
    }

    @Override
    default void setDimension(String dimension) {}
}
