package g_mungus.zps.item;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class AddressPadItem extends Item {
    private static final String POSITIONS_TAG = "positions";

    public AddressPadItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        ListTag positions = tag.getList(POSITIONS_TAG, Tag.TAG_LONG);
        positions.add(LongTag.valueOf(context.getClickedPos().asLong()));
        tag.put(POSITIONS_TAG, positions);

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player) || !level.isClientSide) return;
        if (stack != player.getMainHandItem() && stack != player.getOffhandItem()) return;
        if (Minecraft.getInstance().player != player) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(POSITIONS_TAG, Tag.TAG_LIST)) return;

        ListTag positions = tag.getList(POSITIONS_TAG, Tag.TAG_LONG);
        for (Tag position : positions) {
            long packedPos = ((LongTag) position).getAsLong();
            BlockPos pos = BlockPos.of(packedPos);
            Outliner.getInstance()
                    .showAABB("address_pad_" + player.getUUID() + "_" + pos.asLong(), new AABB(pos))
                    .colored(0x00FFFF)
                    .lineWidth(1 / 16f);
        }
    }
}
