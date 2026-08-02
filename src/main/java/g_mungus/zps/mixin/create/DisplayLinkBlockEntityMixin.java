package g_mungus.zps.mixin.create;

import g_mungus.zps.compat.create.DisplayLinkManualTextAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity", remap = false)
public class DisplayLinkBlockEntityMixin implements DisplayLinkManualTextAccessor {
    @Unique
    private String zps$manualDisplayText = "";

    @Override
    public String zps$getManualDisplayText() {
        return zps$manualDisplayText;
    }

    @Override
    public void zps$setManualDisplayText(String text) {
        String newText = text == null ? "" : text;
        if (newText.equals(zps$manualDisplayText)) {
            return;
        }

        zps$manualDisplayText = newText;
        ((BlockEntity) (Object) this).setChanged();
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void zps$writeManualDisplayText(
            CompoundTag tag,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        tag.putString("ZPSManualDisplayText", zps$manualDisplayText);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void zps$readManualDisplayText(
            CompoundTag tag,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        zps$manualDisplayText = tag.getString("ZPSManualDisplayText");
    }
}
