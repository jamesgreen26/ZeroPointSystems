package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.commands.api.ScriptExecutor;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class OverloadedExecutorArgumentType implements ArgumentType<OverloadedExecutorArgumentType.ParsedOverloads> {
    private static volatile @Nullable Set<ResourceLocation> activeConnectedBlocks = null; // modified clientside only
    private final List<Variant> variants;

    public OverloadedExecutorArgumentType(List<ScriptExecutor<?, ?>> executors) {
        this(executors.stream()
                .map(executor -> new Variant(executor, executor.inputKey(), executor.argumentType(), executor.associatedBlocks()))
                .toList());
    }

    public OverloadedExecutorArgumentType(Collection<Variant> variants) {
        this.variants = List.copyOf(variants);
    }

    public List<Variant> variants() {
        return variants;
    }

    public static void setActiveConnectedBlocks(@Nullable Set<ResourceLocation> connectedBlocks) {
        activeConnectedBlocks = connectedBlocks == null ? null : Set.copyOf(connectedBlocks);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ParsedOverloads parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        int furthestCursor = -1;
        List<ParsedExecutorArgument> parsedArguments = new ArrayList<>();
        CommandSyntaxException bestException = null;

        for (Variant variant : variants) {
            StringReader candidateReader = new StringReader(reader.getString());
            candidateReader.setCursor(start);

            ArgumentType<Object> argumentType = ValueOfOrLiteralArgumentType.of(
                    (ArgumentType<Object>) variant.argumentType(),
                    variant.inputKey()
            );

            try {
                Object rawArgument = argumentType.parse(candidateReader);
                int cursor = candidateReader.getCursor();
                if (cursor > furthestCursor) {
                    furthestCursor = cursor;
                    parsedArguments.clear();
                }
                if (cursor == furthestCursor) {
                    parsedArguments.add(new ParsedExecutorArgument(variant.executor(), rawArgument));
                }
            } catch (CommandSyntaxException exception) {
                if (bestException == null || candidateReader.getCursor() > bestException.getCursor()) {
                    bestException = exception;
                }
            }
        }

        if (parsedArguments.isEmpty()) {
            reader.setCursor(start);
            if (bestException != null) {
                throw bestException;
            }
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
        }

        reader.setCursor(furthestCursor);
        return new ParsedOverloads(List.copyOf(parsedArguments));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<CompletableFuture<Suggestions>> futures = new ArrayList<>();
        for (Variant variant : suggestionVariants()) {
            futures.add(wrappedType(variant).listSuggestions(context, builder.createOffset(builder.getStart())));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> Suggestions.merge(
                        builder.getInput(),
                        futures.stream().map(CompletableFuture::join).toList()
                ));
    }

    @Override
    public Collection<String> getExamples() {
        LinkedHashSet<String> examples = new LinkedHashSet<>();
        for (Variant variant : suggestionVariants()) {
            examples.addAll(wrappedType(variant).getExamples());
            if (examples.size() >= 5) {
                break;
            }
        }
        return examples;
    }

    private List<Variant> suggestionVariants() {
        Set<ResourceLocation> connectedBlocks = activeConnectedBlocks;
        if (connectedBlocks == null) {
            return variants;
        }
        return variants.stream()
                .filter(variant -> variant.associatedBlocks() == null
                        || variant.associatedBlocks().stream().anyMatch(connectedBlocks::contains))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentType<Object> wrappedType(Variant variant) {
        return ValueOfOrLiteralArgumentType.of(
                (ArgumentType<Object>) variant.argumentType(),
                variant.inputKey()
        );
    }

    public record Variant(
            @Nullable ScriptExecutor<?, ?> executor,
            ResourceLocation inputKey,
            ArgumentType<?> argumentType,
            @Nullable Set<ResourceLocation> associatedBlocks
    ) {
    }

    public record ParsedOverloads(List<ParsedExecutorArgument> arguments) {
    }

    public record ParsedExecutorArgument(@Nullable ScriptExecutor<?, ?> executor, Object rawArgument) {
    }
}
