package g_mungus.zps.item;

import g_mungus.zps.client.renderer.ChainsawItemRenderer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

public class ChainsawItem extends PoweredToolItem {
    public ChainsawItem(Item.Properties properties) {
        super(
                properties,
                "item.zps.chainsaw.energy",
                BlockTags.MINEABLE_WITH_AXE,
                BlockTags.MINEABLE_WITH_AXE,
                List.of(BlockTags.MINEABLE_WITH_AXE, BlockTags.MINEABLE_WITH_HOE)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected ChainsawItemRenderer createRenderer() {
        return new ChainsawItemRenderer();
    }
}
