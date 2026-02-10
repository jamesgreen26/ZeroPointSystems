package g_mungus.zps.commands.lang.converters;

import net.minecraftforge.eventbus.api.Event;

import java.util.function.Function;

public class RegisterScriptArgumentProviderConvertersEvent extends Event {
    public <A, B> void register(
            Class<A> from,
            Class<B> to,
            String name,
            Function<A, B> converter
    ) {
        ConverterRegistry.register(from, to, name, converter);
    }
}
