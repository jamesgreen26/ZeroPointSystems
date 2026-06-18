package g_mungus.zps.item;

import g_mungus.zps.client.renderer.PowerDrillItemRenderer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class PowerDrillItem extends PoweredToolItem {
    public PowerDrillItem(Item.Properties properties) {
        super(
                properties,
                Tiers.IRON,
                "item.zps.power_drill.energy",
                BlockTags.MINEABLE_WITH_PICKAXE,
                BlockTags.MINEABLE_WITH_PICKAXE,
                List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected PowerDrillItemRenderer createRenderer() {
        return new PowerDrillItemRenderer();
    }
}
