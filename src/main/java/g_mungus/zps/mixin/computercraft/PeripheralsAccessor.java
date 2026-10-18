package g_mungus.zps.mixin.computercraft;

import dan200.computercraft.impl.Peripherals;
import dan200.computercraft.shared.peripheral.generic.GenericPeripheralProvider;
import dan200.computercraft.shared.platform.InvalidateCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ComputerCraft's generic peripheral provider. On 1.20.1 it only offers generic peripherals for
 * blocks in the level, through {@code Peripherals.getPeripheral}; the provider behind that takes any
 * block entity. Pseudo, as ComputerCraft is optional.
 */
@Pseudo
@Mixin(value = Peripherals.class, remap = false)
public interface PeripheralsAccessor {
    @Accessor("genericProvider")
    static GenericPeripheralProvider<InvalidateCallback> zps$getGenericProvider() {
        throw new AssertionError();
    }
}
