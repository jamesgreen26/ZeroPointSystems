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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import shipwrights.genesis.GenesisMod;
import shipwrights.genesis.space.Celestial;
import shipwrights.genesis.space.Time;

import java.util.Optional;
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
                context -> celestials(context.level())));

        event.register(ZPSNodes.mapper("current", CELESTIALS, CELESTIAL,
                (reg, ctx) -> GenesisMod.celestial(ctx.level()).orElse(null)));

        event.register(ZPSNodes.mapper("nearest", CELESTIALS, CELESTIAL, (reg, ctx) -> {
            ServerLevel level = ctx.level();
            Optional<Celestial> current = GenesisMod.celestial(level);
            if (current.isPresent()) return current.get();
            // Celestial positions are coordinates in space, so there's nothing to measure from anywhere else
            if (!GenesisMod.isSpaceDimension(level)) return null;

            Vec3 world = Compat.toWorldPos(level, ctx.pos().getCenter());
            Vector3dc pos = new Vector3d(world.x, world.y, world.z);
            Time time = Time.get(level);
            Celestial nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Celestial celestial : reg) {
                double distance = celestial.transformProvider().getPosition(reg, time).distanceSquared(pos);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = celestial;
                }
            }
            return nearest;
        }));

        event.register(ZPSNodes.rawArgumentMapper("of", CELESTIALS, CELESTIAL, "celestial",
                CelestialArgument.celestial(), ResourceLocation.class,
                (reg, id, context) -> reg.get(id)));

        // Multiplier on vanilla gravity on the celestial's surface, the overworld's being 1
        event.register(ZPSNodes.mapper("gravity", CELESTIAL, BuiltInTypes.DOUBLE,
                (cel, ctx) -> cel != null ? cel.mass() : 0.0));

        event.register(ZPSNodes.mapper("size", CELESTIAL, BuiltInTypes.DOUBLE,
                (cel, ctx) -> cel != null ? cel.size() : 0.0));

        // Visitable when it has a dimension of its own to arrive in, as Genesis's travel decides
        event.register(ZPSNodes.mapper("is_visitable", CELESTIAL, BuiltInTypes.BOOLEAN, (cel, ctx) -> {
            if (cel == null) return false;
            ResourceLocation key = celestials(ctx.level()).getKey(cel);
            return key != null && !key.equals(GenesisMod.SPACE)
                    && ctx.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, key)) != null;
        }));

        event.register(ZPSNodes.mapper("pos", CELESTIAL, ZPSScriptTypes.VEC_POS, (cel, ctx) -> {
            if (cel == null) return Vec3.ZERO;
            Vector3dc pos = cel.transformProvider().getPosition(celestials(ctx.level()), Time.get(ctx.level()));
            return new Vec3(pos.x(), pos.y(), pos.z());
        }));

        event.register(ZPSNodes.mapper("as_string", CELESTIAL, BuiltInTypes.STRING, (cel, ctx) -> {
            if (cel == null) return "";
            ResourceLocation key = celestials(ctx.level()).getKey(cel);
            return key != null ? key.toString() : "";
        }));

        event.register(ZPSNodes.rawArgumentMapper("==", CELESTIAL, BuiltInTypes.BOOLEAN, "celestial",
                CelestialArgument.celestial(), ResourceLocation.class,
                (cel, id, context) -> cel != null && cel.equals(celestials(context.level()).get(id))));
    }

    private static Registry<Celestial> celestials(Level level) {
        return level.registryAccess().registryOrThrow(Celestial.REGISTRY_KEY);
    }

    public static <S> CompletableFuture<Suggestions> getCelestialSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider source) {
            Registry<?> registry = source.registryAccess().registryOrThrow(Compat.CELESTIALS_KEY);
            return SharedSuggestionProvider.suggestResource(registry.keySet(), builder);
        } else return Suggestions.empty();
    }
}
