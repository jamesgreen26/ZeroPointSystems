package g_mungus.zps.commands.lang.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.exceptions.CancellationException;
import g_mungus.zps.commands.lang.arguments.ArgumentTypeRegistry;
import g_mungus.zps.commands.lang.arguments.MappedArgumentType;
import g_mungus.zps.commands.lang.comparators.ComparisonRegistry;
import g_mungus.zps.commands.lang.providers.ProviderRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class IfUnlessCommand {


    public static <T> LiteralArgumentBuilder<CommandSourceStack> buildForType(CommandDispatcher<CommandSourceStack> dispatcher, Class<T> type, PredicateType predicateType) {

        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(predicateType.key);

        // For every provider of this type
        ProviderRegistry.getAll(type).forEach((providerName, provider) -> {

            LiteralArgumentBuilder<CommandSourceStack> providerNode =
                    Commands.literal(providerName);

            // For every converter from this provider's type
            provider.getConverters().forEach(converter ->
                buildConverterComparisonNodeHelper(providerNode, dispatcher, providerName,
                                                  provider, converter, predicateType)
            );

            // For every comparison of this type
            ComparisonRegistry.getAll(type).forEach((comparisonName, comparison) -> {

                MappedArgumentType<?, T> argType = ArgumentTypeRegistry.get(type);
                assert argType != null;

                buildComparisonNode(providerNode, dispatcher, providerName, provider,
                                    comparisonName, comparison, argType, predicateType);
            });

            root.then(providerNode);
        });

        return root;
    }

    private static <T, I> void buildComparisonNode(
            LiteralArgumentBuilder<CommandSourceStack> providerNode,
            CommandDispatcher<CommandSourceStack> dispatcher,
            String providerName,
            g_mungus.zps.commands.lang.providers.Provider<T> provider,
            String comparisonName,
            g_mungus.zps.commands.lang.comparators.Comparison<T> comparison,
            MappedArgumentType<I, T> argType,
            PredicateType predicateType
    ) {
        providerNode.then(
                Commands.literal(comparisonName)
                        .then(Commands.argument("value", argType.type())
                                .forward(
                                        ZPSCommands.getScriptRootNode(dispatcher),
                                        ctx -> {

                                            I rawArgument = ctx.getArgument("value", argType.argumentClass());

                                            T left = provider.get(ctx);
                                            T right = argType.mapper().apply(rawArgument, ctx.getSource());

                                            if (comparison.test(left, right) == predicateType.desiredResult) {
                                                return Set.of(ctx.getSource());
                                            }

                                            throw new CancellationException(
                                                    Component.literal(
                                                            "Condition failed: "
                                                                    + providerName + " "
                                                                    + comparisonName
                                                    )
                                            );

                                        },
                                        false
                                ))
        );
    }

    private static <T, B> void buildConverterComparisonNodeHelper(
            LiteralArgumentBuilder<CommandSourceStack> providerNode,
            CommandDispatcher<CommandSourceStack> dispatcher,
            String providerName,
            g_mungus.zps.commands.lang.providers.Provider<T> provider,
            g_mungus.zps.commands.lang.converters.Converter<T, B> converter,
            PredicateType predicateType
    ) {
        Class<B> convertedType = converter.getReturnType();

        // For every comparison of the converted type
        ComparisonRegistry.getAll(convertedType).forEach((comparisonName, comparison) -> {
            MappedArgumentType<?, B> argType = ArgumentTypeRegistry.get(convertedType);
            assert argType != null;

            buildConverterComparisonNode(providerNode, dispatcher, providerName, provider,
                                        converter, comparisonName, comparison, argType, predicateType);
        });
    }

    private static <T, B, I> void buildConverterComparisonNode(
            LiteralArgumentBuilder<CommandSourceStack> providerNode,
            CommandDispatcher<CommandSourceStack> dispatcher,
            String providerName,
            g_mungus.zps.commands.lang.providers.Provider<T> provider,
            g_mungus.zps.commands.lang.converters.Converter<T, B> converter,
            String comparisonName,
            g_mungus.zps.commands.lang.comparators.Comparison<B> comparison,
            MappedArgumentType<I, B> argType,
            PredicateType predicateType
    ) {
        String converterName = converter.getName();

        providerNode.then(
                Commands.literal(converterName)
                        .then(Commands.literal(comparisonName)
                                .then(Commands.argument("value", argType.type())
                                        .forward(
                                                ZPSCommands.getScriptRootNode(dispatcher),
                                                ctx -> {
                                                    I rawArgument = ctx.getArgument("value", argType.argumentClass());

                                                    T providerValue = provider.get(ctx);
                                                    B left = converter.convert(providerValue);
                                                    B right = argType.mapper().apply(rawArgument, ctx.getSource());

                                                    if (comparison.test(left, right) == predicateType.desiredResult) {
                                                        return Set.of(ctx.getSource());
                                                    }

                                                    throw new CancellationException(
                                                            Component.literal(
                                                                    "Condition failed: "
                                                                            + providerName + " "
                                                                            + converterName + " "
                                                                            + comparisonName
                                                            )
                                                    );
                                                },
                                                false
                                        ))
                        )
        );
    }

    public enum PredicateType {
        IF("IF", true), UNLESS("UNLESS", false);

        public final String key;
        public final boolean desiredResult;

        PredicateType(String key, boolean desiredResult) {
            this.key = key;
            this.desiredResult = desiredResult;
        }
    }
}
