package g_mungus.zps.commands.lang.providers;

import net.minecraftforge.eventbus.api.Event;

public class RegisterScriptArgumentProvidersEvent extends Event {
    public <T> void register(
            Class<T> type,
            String name,
            Provider<T> provider
    ) {
        ProviderRegistry.register(type, name, provider);
    }


    public <A, B> void registerWithDerivatives(
            Class<A> baseType,
            String name,
            Provider<A> provider,
            Class<B> derivativeType,
            String... derivativeNames
    ) {
        ProviderRegistry.registerWithDerivatives(baseType, name, provider, derivativeType, derivativeNames);
    }
}
