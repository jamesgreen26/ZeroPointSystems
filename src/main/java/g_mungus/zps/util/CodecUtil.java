package g_mungus.zps.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.function.Function;

/** DFU helpers 1.21's DataFixerUpper has built in and 1.20.1's does not. */
public final class CodecUtil {
    private CodecUtil() {
    }

    /** {@code codec.validate(check)}: values that fail the check fail both ways. */
    public static <T> Codec<T> validate(Codec<T> codec, Function<T, DataResult<T>> check) {
        return codec.flatXmap(check, check);
    }
}
