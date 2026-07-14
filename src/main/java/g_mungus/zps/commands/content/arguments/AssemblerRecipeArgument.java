package g_mungus.zps.commands.content.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.commands.content.AssemblerRecipeSupport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A recipe-id argument for the Assembler's {@code set_recipe} command. It parses any resource location and
 * suggests only the recipes the Assembler can fulfill (see {@link AssemblerRecipeSupport}). Actual
 * validation happens in the executor, which has access to the level/recipe manager.
 */
public class AssemblerRecipeArgument implements ArgumentType<ResourceLocation> {

    public static final AssemblerRecipeArgument INSTANCE = new AssemblerRecipeArgument();
    public static final SingletonArgumentInfo<AssemblerRecipeArgument> INFO =
            SingletonArgumentInfo.contextFree(() -> INSTANCE);

    /**
     * Supplies the client level for terminal-side suggestions. Set from client init (see ClientSetup) so
     * this common-side class never references a client-only type — loading a client class on a dedicated
     * server is rejected by the dist checker.
     */
    private static volatile Supplier<Level> clientLevelSupplier = () -> null;

    public static void setClientLevelSupplier(Supplier<Level> supplier) {
        clientLevelSupplier = supplier;
    }

    private AssemblerRecipeArgument() {
    }

    public static AssemblerRecipeArgument recipe() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Level level = levelFor(context.getSource());
        if (level == null) {
            return builder.buildFuture();
        }
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : AssemblerRecipeSupport.fulfillableIds(level)) {
            String candidate = id.toString();
            if (SharedSuggestionProvider.matchesSubStr(input, candidate)) {
                builder.suggest(candidate);
            }
        }
        return builder.buildFuture();
    }

    private static Level levelFor(Object source) {
        if (source instanceof CommandSourceStack commandSource) {
            return commandSource.getLevel();
        }
        // Script-terminal suggestions are computed client-side against the synced recipe manager.
        return clientLevelSupplier.get();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("minecraft:crafting_table", "minecraft:chest");
    }
}
