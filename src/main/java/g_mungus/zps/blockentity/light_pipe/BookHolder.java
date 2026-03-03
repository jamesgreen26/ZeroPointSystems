package g_mungus.zps.blockentity.light_pipe;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface BookHolder {
    @Nullable ItemStack zps$getBook();

    int zps$getCurrentPage();

    void zps$onPageAdded();

    void zps$cyclePages();

    void zps$setPage(int i);

    boolean zps$hasBook();

    default void zps$onPageWritten() {}
}
