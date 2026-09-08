package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * A quantity of one gas produced by a recipe, in kilograms — Kelvin's own unit, so figures line up
 * with what the gauges and the reactor read.
 *
 * <p>The gas is held by id rather than resolved at parse time: recipes load before every mod has
 * necessarily registered its gases, and a gas that never turns up is skipped at craft time rather
 * than failing the whole recipe.
 */
public record GasOutput(ResourceLocation gas, double massKg) {

    public static final Codec<GasOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("gas").forGetter(GasOutput::gas),
            Codec.DOUBLE.validate(amount -> amount > 0
                            ? DataResult.success(amount)
                            : DataResult.error(() -> "Gas amount must be positive, got " + amount))
                    .fieldOf("amount").forGetter(GasOutput::massKg)
    ).apply(instance, GasOutput::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasOutput> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, GasOutput::gas,
            ByteBufCodecs.DOUBLE, GasOutput::massKg,
            GasOutput::new);

    /** The registered gas this names, or null if no mod has registered it. */
    public @Nullable GasType resolve() {
        return GasTypeRegistry.INSTANCE.getGasType(gas);
    }
}
