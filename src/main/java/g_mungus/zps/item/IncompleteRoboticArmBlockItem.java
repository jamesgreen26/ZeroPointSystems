package g_mungus.zps.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IncompleteRoboticArmBlockItem extends BlockItem {
    private final int segmentCount;

    public IncompleteRoboticArmBlockItem(Block block, Properties properties, int segmentCount) {
        super(block, properties);
        this.segmentCount = segmentCount;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zps.incomplete_robotic_arm.segments", segmentCount)
                .withStyle(style -> style.withColor(ShiftTooltipHandler.BASE_COLOR)));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
