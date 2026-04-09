package g_mungus.zps.compat.genesis;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.compat.Compat;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import shipwrights.genesis.GenesisMod;
import shipwrights.genesis.space.Celestial;
import shipwrights.genesis.space.SpaceLevel;

import java.util.concurrent.CompletableFuture;

public class GenesisCompat {

    @SuppressWarnings("unchecked")
    private static Class<Registry<Celestial>> celestialsClass() {
        return (Class<Registry<Celestial>>) (Class<?>) Registry.class;
    }

    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {

        event.register(new ScriptGetter<>(
                "celestial",
                celestialsClass(),
                ResourceLocation.parse("genesis:celestials"),
                context -> GenesisMod.getCelestialRegistry(context.level())
        ));

        event.register(new ScriptMapper<>(
                "current",
                celestialsClass(),
                Celestial.class,
                ResourceLocation.parse("genesis:celestials"),
                ResourceLocation.parse("genesis:celestial"),
                (reg, ctx) -> GenesisMod.getCelestialForLevel(ctx.level())
        ));

        event.register(new ScriptMapper<>(
                "nearest",
                celestialsClass(),
                Celestial.class,
                ResourceLocation.parse("genesis:celestials"),
                ResourceLocation.parse("genesis:celestial"),
                (reg, ctx) -> {
                    ServerLevel level = ctx.level();
                    Celestial current = GenesisMod.getCelestialForLevel(level);
                    if (current != null) return current;
                    if (GenesisMod.isSpaceDimension(level) || GenesisMod.isSubSpaceDimension(level)) {
                        var result = SpaceLevel.nearestCelestialWhere(
                                GenesisMod.getCelestialRegistry(level),
                                VectorConversionsMCKt.toJOML(VSGameUtilsKt.toWorldCoordinates(level, ctx.pos().getCenter())),
                                GenesisMod.getTicks(level),
                                0f,
                                (t) -> true
                                );

                        if (result != null) {
                            return result.getFirst();
                        }
                    }
                    return null;
                }
        ));

        event.register(new ScriptMapper2<>(
                "of",
                celestialsClass(),
                Celestial.class,
                ResourceLocation.parse("genesis:celestials"),
                ResourceLocation.parse("genesis:celestial"),
                "celestial",
                (reg, context) -> reg.get(context.argumentValue()),
                CelestialArgument.celestial(),
                ResourceLocation.class,
                null
        ));

        event.register(new ScriptMapper<>(
                "gravity",
                Celestial.class,
                Double.class,
                ResourceLocation.parse("genesis:celestial"),
                ResourceLocation.parse("zps:double"),
                (cel, ctx) -> cel != null ? cel.gravity() : 0.0
        ));

        event.register(new ScriptMapper<>(
                "size",
                Celestial.class,
                Double.class,
                ResourceLocation.parse("genesis:celestial"),
                ResourceLocation.parse("zps:double"),
                (cel, ctx) -> cel != null ? cel.size() : 0.0
        ));

        event.register(new ScriptMapper<>(
                "is_visitable",
                Celestial.class,
                Boolean.class,
                ResourceLocation.parse("genesis:celestial"),
                ResourceLocation.parse("zps:boolean"),
                (cel, ctx) -> cel != null && cel.type().isVisitable()
        ));

        event.register(new ScriptMapper<>(
                "pos",
                Celestial.class,
                Vec3.class,
                ResourceLocation.parse("genesis:celestial"),
                ResourceLocation.parse("zps:vec_pos"),
                (cel, ctx) -> {
                    if (cel == null) return Vec3.ZERO;
                    Registry<Celestial> reg = GenesisMod.getCelestialRegistry(ctx.level());
                    var pos = cel.getPosition(GenesisMod.getTicks(ctx.level()), reg);
                    return new Vec3(pos.x(), pos.y(), pos.z());
                }
        ));

        event.register(new ScriptMapper2<>(
                "==",
                Celestial.class,
                Boolean.class,
                ResourceLocation.parse("genesis:celestial"),
                ResourceLocation.parse("zps:boolean"),
                "celestial",
                (cel, context) -> {
                    ResourceLocation id = context.argumentValue();
                    Registry<Celestial> reg = GenesisMod.getCelestialRegistry(context.level());
                    return cel != null && cel.equals(reg.get(id));
                },
                CelestialArgument.celestial(),
                ResourceLocation.class,
                null
        ));
    }

    public static <S> CompletableFuture<Suggestions> getCelestialSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider source) {
            Registry<?> registry = source.registryAccess().registryOrThrow(Compat.CELESTIALS_KEY);
            return SharedSuggestionProvider.suggestResource(registry.keySet(), builder);
        } else return Suggestions.empty();
    }
}
