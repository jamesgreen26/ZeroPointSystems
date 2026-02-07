package g_mungus.zps.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BlockPosListArgument implements ArgumentType<List<Coordinates>> {

    private static final SimpleCommandExceptionType ERROR_EXPECTED_OPEN =
            new SimpleCommandExceptionType(Component.literal("Expected '[' to start block position list"));

    private static final SimpleCommandExceptionType ERROR_EXPECTED_CLOSE =
            new SimpleCommandExceptionType(Component.literal("Expected ']' to end block position list"));

    private static final Collection<String> EXAMPLES = List.of(
            "[0 64 0]",
            "[0 64 0, 10 70 -5]",
            "[~ ~ ~, ^1 ^ ^-3]"
    );

    public static BlockPosListArgument blockPosList() {
        return new BlockPosListArgument();
    }

    @Override
    public List<Coordinates> parse(StringReader reader) throws CommandSyntaxException {
        List<Coordinates> result = new ArrayList<>();
        BlockPosArgument single = BlockPosArgument.blockPos();

        reader.skipWhitespace();

        // Expect '['
        if (!reader.canRead() || reader.peek() != '[') {
            throw ERROR_EXPECTED_OPEN.createWithContext(reader);
        }
        reader.skip();

        while (true) {
            reader.skipWhitespace();

            // End of list
            if (reader.canRead() && reader.peek() == ']') {
                reader.skip();
                break;
            }

            Coordinates coords = single.parse(reader);
            result.add(coords);

            reader.skipWhitespace();

            if (!reader.canRead()) {
                throw ERROR_EXPECTED_CLOSE.createWithContext(reader);
            }

            char c = reader.peek();
            if (c == ',') {
                reader.skip();
            } else if (c == ']') {
                reader.skip();
                break;
            } else {
                throw ERROR_EXPECTED_CLOSE.createWithContext(reader);
            }
        }

        return result;
    }

    /**
     * Convenience accessor to get resolved BlockPos list from command context
     */
    public static List<BlockPos> getBlockPosList(
            CommandContext<CommandSourceStack> ctx,
            String name
    ) {
        List<Coordinates> coords = ctx.getArgument(name, List.class);
        return coords.stream()
                .map(c -> c.getBlockPos(ctx.getSource()))
                .toList();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder
    ) {
        if (!(context.getSource() instanceof SharedSuggestionProvider)) {
            return Suggestions.empty();
        }

        String remaining = builder.getRemaining();

        // Nothing typed yet → suggest '['
        if (remaining.isEmpty()) {
            return builder.suggest("[").buildFuture();
        }

        // Typed something, but not inside brackets yet
        if (!remaining.startsWith("[")) {
            return Suggestions.empty();
        }

        // Already closed → no more suggestions
        if (remaining.contains("]")) {
            return Suggestions.empty();
        }

        // Check if we just finished typing a complete coordinate
        if (isCompleteCoordinate(remaining)) {
            builder.suggest(", ");
            builder.suggest("]");
            return builder.buildFuture();
        }

        int suggestionStart = findCoordinateSuggestionStart(builder.getInput());
        SuggestionsBuilder coordBuilder = builder.createOffset(suggestionStart);
        return BlockPosArgument.blockPos().listSuggestions(context, coordBuilder);
    }

    private int findCoordinateSuggestionStart(String fullInput) {
        int lastComma = fullInput.lastIndexOf(',');
        int start = (lastComma != -1) ? lastComma + 1 : fullInput.indexOf('[') + 1;

        // Skip up to one space after the comma or bracket
        if (start < fullInput.length() && fullInput.charAt(start) == ' ') {
            start++;
        }

        return start;
    }

    private boolean isCompleteCoordinate(String remaining) {
        try {
            String toCheck = getCoordinateString(remaining);

            if (toCheck.isEmpty()) {
                return false;
            }

            StringReader testReader = new StringReader(toCheck);
            BlockPosArgument.blockPos().parse(testReader);

            // Complete coordinate if we consumed everything
            return !testReader.canRead();
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static @NotNull String getCoordinateString(String remaining) {
        int lastCommaIndex = remaining.lastIndexOf(',');
        int startIndex = lastCommaIndex == -1 ? 1 : lastCommaIndex + 1;
        String afterLastComma = remaining.substring(startIndex);

        // Skip up to one leading space
        if (afterLastComma.startsWith(" ")) {
            afterLastComma = afterLastComma.substring(1);
        }

        // Remove up to one trailing space for the check
        String toCheck = afterLastComma;
        if (toCheck.endsWith(" ")) {
            toCheck = toCheck.substring(0, toCheck.length() - 1);
        }
        return toCheck;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
