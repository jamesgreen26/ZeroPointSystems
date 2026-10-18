package g_mungus.zps.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.21's {@code @GameTest(skyAccess = true)}, which 1.20.1 lacks: the test's arena is walled in
 * barriers but left without a roof. Every other arena is roofed too; see {@code GameTestInfoMixin}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SkyAccess {

    /** Names of the tests marked, as their test functions are named. Filled as tests are registered. */
    Set<String> TESTS = ConcurrentHashMap.newKeySet();
}
