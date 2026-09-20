package g_mungus.zps.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Asks an {@link ImpactResult} to bury whatever the struck block would have dropped had it been
 * mined, rather than an item named by the recipe. This is what lets a single recipe keyed on an ore
 * tag cover every ore in it, modded ones included: each ore's own loot table decides what ends up
 * in the suspicious block.
 *
 * @param fortune level of Fortune on the notional pickaxe the loot table is rolled with; 0 for none
 */
public record BuriedDrops(int fortune) {
    public static final Codec<BuriedDrops> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fortune", 0).forGetter(BuriedDrops::fortune)
    ).apply(instance, BuriedDrops::new));

    public static final StreamCodec<ByteBuf, BuriedDrops> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(BuriedDrops::new, BuriedDrops::fortune);

    /**
     * The tool handed to the struck block's loot table. Netherite so that no tier-gated loot
     * condition can turn the drops away, and enchanted per {@link #fortune}.
     */
    public ItemStack createTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (fortune > 0) {
            tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), fortune);
        }
        return tool;
    }
}
