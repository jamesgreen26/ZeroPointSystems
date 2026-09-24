package g_mungus.zps.client.renderer;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** Draws nothing: the vehicle is the inside of a duct, and its rider is hidden as well. */
public class DuctTravelRenderer extends EntityRenderer<DuctTravelEntity> {
    public DuctTravelRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DuctTravelEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "textures/entity/empty.png");
    }
}
