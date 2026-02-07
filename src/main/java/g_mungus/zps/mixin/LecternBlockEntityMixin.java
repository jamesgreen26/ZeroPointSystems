package g_mungus.zps.mixin;

import g_mungus.zps.blockentity.light_pipe.BookHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityMixin implements BookHolder {

    @Shadow public abstract int getPage();

    @Override
    public @Nullable ItemStack zps$getBook() {
        return getBook();
    }

    @Override
    public int zps$getCurrentPage() {
        return getPage();
    }

    @Shadow
    public ItemStack getBook() {
        return null;
    }

    @Override
    public void zps$cyclePages() {
        if (zps$hasNextPage()) {
            setPage(getPage() + 1);
        } else {
            setPage(0);
        }
    }

    @Shadow
    void setPage(int i) {
    }


    @Unique
    boolean zps$hasNextPage() {
        return getPage() + 1 < pageCount;
    }

    @Override
    public boolean zps$hasBook() {
        return hasBook();
    }

    @Shadow
    public boolean hasBook() {
        return false;
    }

    @Shadow
    private int pageCount;
}
