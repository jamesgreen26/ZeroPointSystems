package g_mungus.zps.util;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The part of 1.21.1's {@code net.minecraft.network.codec.StreamCodec} ZPS uses, so code ported
 * from there keeps its shape: a value written to and read back from a buffer.
 */
public interface StreamCodec<B, V> {
    V decode(B buffer);

    void encode(B buffer, V value);

    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return decoder.apply(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.accept(buffer, value);
            }
        };
    }

    /** Writes nothing and always reads back {@code value}. */
    static <B, V> StreamCodec<B, V> unit(V value) {
        return of((buffer, ignored) -> { }, buffer -> value);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return of((buffer, value) -> self.encode(buffer, from.apply(value)), buffer -> to.apply(self.decode(buffer)));
    }
}
