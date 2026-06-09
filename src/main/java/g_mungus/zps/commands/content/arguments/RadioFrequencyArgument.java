package g_mungus.zps.commands.content.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class RadioFrequencyArgument implements ArgumentType<Integer> {

    private static final List<String> FREQUENCIES = List.of(
            "05KHz", "10KHz", "15KHz", "20KHz",
            "25KHz", "30KHz", "35KHz", "40KHz",
            "45KHz", "50KHz", "55KHz", "60KHz",
            "65KHz", "70KHz", "75KHz", "80KHz"
    );

    private static final DynamicCommandExceptionType ERROR_INVALID_FREQUENCY =
            new DynamicCommandExceptionType(arg ->
                    Component.literal("Invalid radio frequency: " + arg)
            );

    public static final RadioFrequencyArgument INSTANCE = new RadioFrequencyArgument();
    public static final SingletonArgumentInfo<RadioFrequencyArgument> INFO =
            SingletonArgumentInfo.contextFree(() -> INSTANCE);

    private RadioFrequencyArgument() {}

    public static RadioFrequencyArgument radioFrequency() {
        return INSTANCE;
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String input = reader.readUnquotedString();

        int index = FREQUENCIES.indexOf(input);

        if (index == -1) {
            reader.setCursor(start);
            throw ERROR_INVALID_FREQUENCY.createWithContext(reader, input);
        }

        return index; // Return 0–15
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context,
                                                              SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String freq : FREQUENCIES) {
            if (SharedSuggestionProvider.matchesSubStr(input, freq.toLowerCase(Locale.ROOT))) {
                builder.suggest(freq);
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("05KHz", "40KHz", "80KHz");
    }
}