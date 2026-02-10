package g_mungus.zps.commands.lang.converters;

import net.minecraftforge.eventbus.api.Event;

public class RegisterScriptArgumentProviderConvertersEvent extends Event {
    public <A, B> void register(
            Class<A> from,
            Class<B> to,
            String name,
            Converter<A, B> converter
    ) {
        ProviderConverters.register(from, to, name, converter);
    }
}
