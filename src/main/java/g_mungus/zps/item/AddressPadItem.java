package g_mungus.zps.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

public class AddressPadItem extends Item {
    private static final String POSITIONS_TAG = "positions";

    public AddressPadItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    AddressPadClientHooks.openNameEntryScreen(context.getHand(), context.getClickedPos()));
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    public static void putNamedPosition(ItemStack stack, String name, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag positions = tag.getCompound(POSITIONS_TAG);
        positions.putLong(name, pos.asLong());
        tag.put(POSITIONS_TAG, positions);
    }

    public static CompoundTag getPositions(ItemStack stack) {
        return stack.getOrCreateTag().getCompound(POSITIONS_TAG);
    }
}
