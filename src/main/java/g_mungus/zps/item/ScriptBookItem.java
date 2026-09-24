package g_mungus.zps.item;

import g_mungus.zps.client.screens.ScriptTerminalScreen;
import g_mungus.zps.manual.ModManuals;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ScriptBookItem extends Item {

    public ScriptBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ModManuals.openScriptCommandsManual(level);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
