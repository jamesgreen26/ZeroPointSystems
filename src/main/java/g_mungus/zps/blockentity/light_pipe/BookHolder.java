package g_mungus.zps.blockentity.light_pipe;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface BookHolder {
    @Nullable ItemStack zps$getBook();

    int zps$getCurrentPage();

    default void onPageWritten() {}
}
