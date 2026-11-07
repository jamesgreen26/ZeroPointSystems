package g_mungus.zps.mixin;

import g_mungus.zps.world.AtmosphereTemperature;
import g_mungus.zps.world.SpaceTemperature;
import net.minecraft.world.level.biome.Biome;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * Adds {@link AtmosphereTemperature}'s and {@link SpaceTemperature}'s constants to the enum. Forge's
 * enum extension needs {@code IExtensibleEnum}, which this enum lacks, and it is abstract with a body
 * per constant, so each new constant is another instance of {@code NONE}'s body class
 * ({@code TemperatureModifierNoneMixin} gives them their own behaviour). This runs inside the enum, a
 * nestmate of that class, so the lookup can reach its private constructor. It must land before
 * {@code CODEC} snapshots the values.
 */
@Mixin(Biome.TemperatureModifier.class)
public abstract class TemperatureModifierMixin {
    @Shadow @Final public static Biome.TemperatureModifier NONE;
    @Shadow @Final @Mutable private static Biome.TemperatureModifier[] $VALUES;

    @Inject(method = "<clinit>", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/level/biome/Biome$TemperatureModifier;$VALUES:[Lnet/minecraft/world/level/biome/Biome$TemperatureModifier;"))
    private static void zps$addModifiers(CallbackInfo ci) {
        zps$add(AtmosphereTemperature.NAME, AtmosphereTemperature.SERIALIZED_NAME);
        zps$add(SpaceTemperature.NAME, SpaceTemperature.SERIALIZED_NAME);
    }

    private static void zps$add(String name, String serializedName) {
        Biome.TemperatureModifier modifier;
        try {
            modifier = (Biome.TemperatureModifier) MethodHandles.lookup()
                    .findConstructor(NONE.getClass(), MethodType.methodType(void.class, String.class, int.class, String.class))
                    .invoke(name, $VALUES.length, serializedName);
        } catch (Throwable e) {
            throw new IllegalStateException("Could not add the " + serializedName + " temperature modifier", e);
        }
        $VALUES = Arrays.copyOf($VALUES, $VALUES.length + 1);
        $VALUES[$VALUES.length - 1] = modifier;
    }
}
