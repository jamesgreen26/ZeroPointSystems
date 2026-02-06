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
     * Convenience accessor
     */
    public static List<BlockPos> getBlockPosList(
            CommandContext<CommandSourceStack> ctx,
            String name
    ) {
        List<Coordinates> coords = ctx.getArgument(name, List.class);
        List<BlockPos> result = new ArrayList<>(coords.size());

        for (Coordinates c : coords) {
            result.add(c.getBlockPos(ctx.getSource()));
        }

        return result;
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

        // Suggest coordinates
        if (remaining.equals("[") || remaining.endsWith(",") || remaining.endsWith(", ")) {
            // Find the index of the last comma in the *full input* (not remaining)
            int lastComma = builder.getInput().lastIndexOf(',');
            if (remaining.endsWith(", ")) lastComma++;

            // Offset is just after the comma, or after the '[' if no comma yet
            int offset = (lastComma != -1) ? lastComma + 1 : builder.getInput().indexOf('[') + 1;

            SuggestionsBuilder coordBuilder = builder.createOffset(offset);

            return BlockPosArgument.blockPos()
                    .listSuggestions(context, coordBuilder);
        }


        // Inside brackets, but already closed → stop suggesting
        if (remaining.contains("]")) {
            return Suggestions.empty();
        }

        // Try to detect "just finished a coordinate"
        try {
            int index = remaining.lastIndexOf(",");
            String inside = remaining.substring(Math.max(1, index)).trim();

            if (!inside.isEmpty()) {
                StringReader testReader = new StringReader(inside);
                BlockPosArgument.blockPos().parse(testReader);

                // If we consumed everything, we finished a coordinate
                if (!testReader.canRead()) {
                    builder.suggest(", ");
                    builder.suggest("]");
                    return builder.buildFuture();
                }
            }
        } catch (CommandSyntaxException ignored) {
            // Incomplete coordinate → no structural suggestions yet
        }

        // Inside bracketed list → delegate to BlockPosArgument
        return BlockPosArgument.blockPos()
                .listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
