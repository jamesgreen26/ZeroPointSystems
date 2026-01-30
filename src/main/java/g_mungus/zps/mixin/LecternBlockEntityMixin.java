package g_mungus.zps.mixin;

import g_mungus.zps.blockentity.light_pipe.BookHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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
}
