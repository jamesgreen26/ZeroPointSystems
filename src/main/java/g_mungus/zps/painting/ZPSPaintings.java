package g_mungus.zps.painting;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ZPSPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(ForgeRegistries.PAINTING_VARIANTS, ZPSMod.MOD_ID);

    public static final RegistryObject<PaintingVariant> QUASAR = PAINTING_VARIANTS.register("quasar",
            () -> new PaintingVariant(48, 48));
}
