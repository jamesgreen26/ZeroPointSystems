package g_mungus.zps.tractor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What a block yields when the beam takes it: its silk-touch drops, from its own loot table.
 * <p>
 * This is deliberately not {@code Block.asItem()}. That is the block's placing item, which is the wrong thing
 * for anything placed by a non-block item (powder snow's is the bucket) and ignores the loot table altogether.
 */
public final class SilkDrops {
    private SilkDrops() {
    }

    public static List<ItemStack> of(ServerLevel level, BlockState state, BlockPos pos) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(Enchantments.SILK_TOUCH, 1);
        return Block.getDrops(state, level, pos, null, null, tool);
    }
}
