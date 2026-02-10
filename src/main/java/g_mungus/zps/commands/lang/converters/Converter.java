package g_mungus.zps.commands.lang.converters;

public interface Converter<A, B> {
    B convert(A value);

    Class<B> getReturnType();

    String getName();
}
