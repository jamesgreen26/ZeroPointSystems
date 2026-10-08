package g_mungus.zps.client.script;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.commands.api.ScriptTarget;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The client's suggestion source, as a script editor uses it: it also knows what the script will
 * be aimed at, so suggestions leave out what applies to none of it.
 *
 * @param connectedTargets what the script can be aimed at, or null when that is not known, which
 *                         leaves nothing out
 */
public record EditorSuggestionSource(SharedSuggestionProvider delegate, @Nullable Set<ScriptTarget> connectedTargets)
        implements SharedSuggestionProvider {

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return delegate.getOnlinePlayerNames();
    }

    @Override
    public Collection<String> getCustomTabSugggestions() {
        return delegate.getCustomTabSugggestions();
    }

    @Override
    public Collection<String> getSelectedEntities() {
        return delegate.getSelectedEntities();
    }

    @Override
    public Collection<String> getAllTeams() {
        return delegate.getAllTeams();
    }

    @Override
    public Stream<ResourceLocation> getAvailableSounds() {
        return delegate.getAvailableSounds();
    }

    @Override
    public Stream<ResourceLocation> getRecipeNames() {
        return delegate.getRecipeNames();
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context) {
        return delegate.customSuggestion(context);
    }

    @Override
    public Collection<TextCoordinates> getRelevantCoordinates() {
        return delegate.getRelevantCoordinates();
    }

    @Override
    public Collection<TextCoordinates> getAbsoluteCoordinates() {
        return delegate.getAbsoluteCoordinates();
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return delegate.levels();
    }

    @Override
    public RegistryAccess registryAccess() {
        return delegate.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> registry,
                                                                  ElementSuggestionType type,
                                                                  SuggestionsBuilder builder,
                                                                  CommandContext<?> context) {
        return delegate.suggestRegistryElements(registry, type, builder, context);
    }

    @Override
    public boolean hasPermission(int level) {
        return delegate.hasPermission(level);
    }
}
