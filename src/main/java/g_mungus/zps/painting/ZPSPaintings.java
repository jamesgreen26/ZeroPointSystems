package g_mungus.zps.painting;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ZPSPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, ZPSMod.MOD_ID);

    public static final DeferredHolder<PaintingVariant, PaintingVariant> QUASAR = PAINTING_VARIANTS.register("quasar",
            () -> new PaintingVariant(48, 48, ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "quasar")));

    public static final DeferredHolder<PaintingVariant, PaintingVariant> LOGO = PAINTING_VARIANTS.register("logo",
            () -> new PaintingVariant(64, 64, ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "logo")));
}
