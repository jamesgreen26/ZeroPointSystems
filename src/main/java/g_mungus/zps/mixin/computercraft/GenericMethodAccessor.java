package g_mungus.zps.mixin.computercraft;

import dan200.computercraft.core.asm.GenericMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.lang.reflect.Method;

/** The Java method behind one of ComputerCraft's generic peripheral methods, and what it applies to. */
@Pseudo
@Mixin(value = GenericMethod.class, remap = false)
public interface GenericMethodAccessor {
    @Accessor("method")
    Method zps$getMethod();

    @Accessor("target")
    Class<?> zps$getTarget();
}
