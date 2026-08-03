package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.commands.api.ScriptExecutor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class OverloadedExecutorArgumentTypeTest {
    private static final ResourceLocation INT_KEY = ResourceLocation.parse("zps:int");
    private static final ResourceLocation BOOLEAN_KEY = ResourceLocation.parse("zps:boolean");
    private static final ResourceLocation BLOCK_A = ResourceLocation.parse("zps:block_a");
    private static final ResourceLocation BLOCK_B = ResourceLocation.parse("zps:block_b");

    private static final ScriptExecutor<Integer, Integer> INT_EXECUTOR = ScriptExecutor.simpleWithBlocks(
            "set_mode",
            Integer.class,
            INT_KEY,
            IntegerArgumentType.integer(),
            (value, context) -> value,
            Set.of(BLOCK_A)
    );

    private static final ScriptExecutor<Boolean, Boolean> BOOLEAN_EXECUTOR = ScriptExecutor.simpleWithBlocks(
            "set_mode",
            Boolean.class,
            BOOLEAN_KEY,
            BoolArgumentType.bool(),
            (value, context) -> value ? 1 : 0,
            Set.of(BLOCK_B)
    );

    @Test
    public void parseKeepsOnlyExecutorsWhoseArgumentTypeMatchesTheInput() throws Exception {
        OverloadedExecutorArgumentType argumentType = new OverloadedExecutorArgumentType(List.of(INT_EXECUTOR, BOOLEAN_EXECUTOR));
        StringReader reader = new StringReader("12");

        OverloadedExecutorArgumentType.ParsedOverloads parsed = argumentType.parse(reader);

        assertEquals(2, reader.getCursor(), "Parser should advance past the accepted argument");
        assertEquals(1, parsed.arguments().size());
        assertSame(INT_EXECUTOR, parsed.arguments().get(0).executor());
        assertEquals(12, parsed.arguments().get(0).rawArgument());
    }

    @Test
    public void parsePreservesMultipleMatchesForRuntimeBlockResolution() throws Exception {
        ScriptExecutor<Integer, Integer> clampedIntExecutor = ScriptExecutor.simpleWithBlocks(
                "set_mode",
                Integer.class,
                INT_KEY,
                IntegerArgumentType.integer(0, 15),
                (value, context) -> value,
                Set.of(BLOCK_B)
        );
        OverloadedExecutorArgumentType argumentType = new OverloadedExecutorArgumentType(List.of(INT_EXECUTOR, clampedIntExecutor));
        StringReader reader = new StringReader("7");

        OverloadedExecutorArgumentType.ParsedOverloads parsed = argumentType.parse(reader);

        assertEquals(2, parsed.arguments().size(),
                "Overlapping parses must survive so execution can choose by target block");
        assertSame(INT_EXECUTOR, parsed.arguments().get(0).executor());
        assertSame(clampedIntExecutor, parsed.arguments().get(1).executor());
    }

    @Test
    public void suggestionsOnlyUseVariantsApplicableToActiveConnectedBlocks() {
        OverloadedExecutorArgumentType argumentType = new OverloadedExecutorArgumentType(List.of(
                new OverloadedExecutorArgumentType.Variant(null, INT_KEY, new SuggestionArgumentType("alpha"), Set.of(BLOCK_A)),
                new OverloadedExecutorArgumentType.Variant(null, INT_KEY, new SuggestionArgumentType("beta"), Set.of(BLOCK_B))
        ));

        OverloadedExecutorArgumentType.setActiveConnectedBlocks(Set.of(BLOCK_A));
        Suggestions suggestions = argumentType.listSuggestions(null, new SuggestionsBuilder("", 0)).join();
        OverloadedExecutorArgumentType.setActiveConnectedBlocks(null);

        assertEquals(List.of("alpha"), suggestions.getList().stream().map(suggestion -> suggestion.getText()).toList());
    }

    private record SuggestionArgumentType(String suggestion) implements ArgumentType<String> {
        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            return reader.readUnquotedString();
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            return builder.suggest(suggestion).buildFuture();
        }

        @Override
        public Collection<String> getExamples() {
            return List.of(suggestion);
        }
    }
}
