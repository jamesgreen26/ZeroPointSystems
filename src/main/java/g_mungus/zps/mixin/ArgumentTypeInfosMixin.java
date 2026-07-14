package g_mungus.zps.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentTypeInfo;
import g_mungus.zps.commands.content.arguments.AssemblerRecipeArgument;
import g_mungus.zps.commands.content.arguments.BlockPosListArgument;
import g_mungus.zps.compat.genesis.CelestialArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArgumentTypeInfos.class)
public class ArgumentTypeInfosMixin {

    @Shadow
    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> ArgumentTypeInfo<A, T> register(Registry<ArgumentTypeInfo<?, ?>> arg, String string, Class<? extends A> class_, ArgumentTypeInfo<A, T> arg2) {
        return null;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void bootstrapInject(Registry<ArgumentTypeInfo<?, ?>> arg, CallbackInfoReturnable<ArgumentTypeInfo<?, ?>> cir) {
        register(
                arg,
                "zps:block_pos_list",
                BlockPosListArgument.class,
                SingletonArgumentInfo.contextFree(BlockPosListArgument::blockPosList)
        );

        register(
                arg,
                "zps:value_of_or_literal",
                (Class<? extends ValueOfOrLiteralArgumentType<?>>) (Class<?>) ValueOfOrLiteralArgumentType.class,
                ValueOfOrLiteralArgumentTypeInfo.INSTANCE
        );

        register(arg, "zps:celestial", CelestialArgument.class, CelestialArgument.INFO);

        register(
                arg,
                "zps:recipe",
                AssemblerRecipeArgument.class,
                AssemblerRecipeArgument.INFO
        );
    }
}
