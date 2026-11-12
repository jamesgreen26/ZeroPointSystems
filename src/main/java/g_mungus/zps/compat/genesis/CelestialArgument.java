package g_mungus.zps.compat.genesis;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.compat.Compat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CelestialArgument implements ArgumentType<ResourceLocation> {

    private static final DynamicCommandExceptionType ERROR_INVALID_VALUE =
            new DynamicCommandExceptionType(arg ->
                    Component.literal("Unknown celestial: " + arg));

    public static final SingletonArgumentInfo<CelestialArgument> INFO =
            SingletonArgumentInfo.contextFree(CelestialArgument::celestial);

    private static final Collection<String> EXAMPLES = List.of("genesis:sun", "minecraft:overworld");

    public static CelestialArgument celestial() {
        return new CelestialArgument();
    }

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (Compat.isGenesisLoaded()) {
            return GenesisCompat.getCelestialSuggestions(context, builder);
        }

        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
