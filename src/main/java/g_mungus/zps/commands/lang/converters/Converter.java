package g_mungus.zps.commands.lang.converters;

@FunctionalInterface
public interface Converter<A, B> {
    B convert(A value);
}
