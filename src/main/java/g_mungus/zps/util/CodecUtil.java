package g_mungus.zps.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.function.Function;
import java.util.stream.Stream;

/** DFU helpers 1.21's DataFixerUpper has built in and 1.20.1's does not. */
public final class CodecUtil {
    private CodecUtil() {
    }

    /** {@code codec.validate(check)}: values that fail the check fail both ways. */
    public static <T> Codec<T> validate(Codec<T> codec, Function<T, DataResult<T>> check) {
        return codec.flatXmap(check, check);
    }

    /**
     * {@code codec.optionalFieldOf(name, defaultValue)} as 1.21 has it: a missing field is the
     * default, but one that is there and fails to decode is an error. 1.20.1's quietly falls back to
     * the default instead.
     */
    public static <A> MapCodec<A> optionalFieldOf(Codec<A> codec, String name, A defaultValue) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(defaultValue) : codec.parse(ops, value);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.equals(defaultValue) ? prefix : prefix.add(name, codec.encodeStart(ops, input));
            }
        };
    }
}
