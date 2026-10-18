package g_mungus.zps.mixin;

import g_mungus.zps.gametest.SkyAccess;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Narrows the tests a gametest server runs to those named by the {@code zps.gametestFilter} system property: a
 * comma-separated list, each matching any test whose name contains it, ignoring case. Unset or blank runs them all.
 * The build passes it from {@code -PgametestFilter=...}.
 */
@Mixin(GameTestRegistry.class)
public class GameTestRegistryMixin {

    /** Remembers which tests want no roof, since 1.20.1's test functions cannot say so; see {@link SkyAccess}. */
    @Inject(method = "turnMethodIntoTestFunction", at = @At("RETURN"))
    private static void zps$noteSkyAccess(Method method, CallbackInfoReturnable<TestFunction> cir) {
        if (method.isAnnotationPresent(SkyAccess.class)) {
            SkyAccess.TESTS.add(cir.getReturnValue().getTestName());
        }
    }

    @Inject(method = "getAllTestFunctions", at = @At("RETURN"), cancellable = true)
    private static void zps$filterTests(CallbackInfoReturnable<Collection<TestFunction>> cir) {
        String filter = System.getProperty("zps.gametestFilter", "").strip();
        if (filter.isEmpty()) {
            return;
        }
        List<String> terms = Arrays.stream(filter.toLowerCase(Locale.ROOT).split(","))
                .map(String::strip)
                .filter(term -> !term.isEmpty())
                .toList();
        cir.setReturnValue(cir.getReturnValue().stream()
                .filter(test -> terms.stream().anyMatch(test.getTestName().toLowerCase(Locale.ROOT)::contains))
                .toList());
    }
}
