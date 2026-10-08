package g_mungus.zps.compat.genesis;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptTypes;
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
    private static final ScriptType<Registry<Celestial>> CELESTIALS =
            ScriptType.opaque(new TypeKey("genesis", "celestials"), (Class<Registry<Celestial>>) (Class<?>) Registry.class);

    private static final ScriptType<Celestial> CELESTIAL = ScriptType.opaque(new TypeKey("genesis", "celestial"), Celestial.class);

    public static void registerScriptTypes(RegisterScriptTypesEvent event) {
        event.register(CELESTIALS);
        event.register(CELESTIAL);
    }

    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {

        event.register(ZPSNodes.getter("celestial", CELESTIALS,
                context -> GenesisMod.getCelestialRegistry(context.level())));

        event.register(ZPSNodes.mapper("current", CELESTIALS, CELESTIAL,
                (reg, ctx) -> GenesisMod.getCelestialForLevel(ctx.level())));

        event.register(ZPSNodes.mapper("nearest", CELESTIALS, CELESTIAL, (reg, ctx) -> {
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
        }));

        event.register(ZPSNodes.rawArgumentMapper("of", CELESTIALS, CELESTIAL, "celestial",
                CelestialArgument.celestial(), ResourceLocation.class,
                (reg, id, context) -> reg.get(id)));

        event.register(ZPSNodes.mapper("gravity", CELESTIAL, BuiltInTypes.DOUBLE,
                (cel, ctx) -> cel != null ? cel.gravity() : 0.0));

        event.register(ZPSNodes.mapper("size", CELESTIAL, BuiltInTypes.DOUBLE,
                (cel, ctx) -> cel != null ? cel.size() : 0.0));

        event.register(ZPSNodes.mapper("is_visitable", CELESTIAL, BuiltInTypes.BOOLEAN,
                (cel, ctx) -> cel != null && cel.type().isVisitable()));

        event.register(ZPSNodes.mapper("pos", CELESTIAL, ZPSScriptTypes.VEC_POS, (cel, ctx) -> {
            if (cel == null) return Vec3.ZERO;
            Registry<Celestial> reg = GenesisMod.getCelestialRegistry(ctx.level());
            var pos = cel.getPosition(GenesisMod.getTicks(ctx.level()), reg);
            return new Vec3(pos.x(), pos.y(), pos.z());
        }));

        event.register(ZPSNodes.mapper("as_string", CELESTIAL, BuiltInTypes.STRING, (cel, ctx) -> {
            if (cel == null) return "";
            ResourceLocation key = GenesisMod.getCelestialRegistry(ctx.level()).getKey(cel);
            return key != null ? key.toString() : "";
        }));

        event.register(ZPSNodes.rawArgumentMapper("==", CELESTIAL, BuiltInTypes.BOOLEAN, "celestial",
                CelestialArgument.celestial(), ResourceLocation.class, (cel, id, context) -> {
                    Registry<Celestial> reg = GenesisMod.getCelestialRegistry(context.level());
                    return cel != null && cel.equals(reg.get(id));
                }));
    }

    public static <S> CompletableFuture<Suggestions> getCelestialSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider source) {
            Registry<?> registry = source.registryAccess().registryOrThrow(Compat.CELESTIALS_KEY);
            return SharedSuggestionProvider.suggestResource(registry.keySet(), builder);
        } else return Suggestions.empty();
    }
}
