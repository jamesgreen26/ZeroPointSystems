package g_mungus.zps.client.renderer;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DodecaMountingEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DodecaMountingRenderer extends EntityRenderer<DodecaMountingEntity> {
    public DodecaMountingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DodecaMountingEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "textures/entity/empty.png");
    }
}
