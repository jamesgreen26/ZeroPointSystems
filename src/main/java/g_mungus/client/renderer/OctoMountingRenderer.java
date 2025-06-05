package g_mungus.client.renderer;

import g_mungus.ZPSMod;
import g_mungus.entity.OctoMountingEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class OctoMountingRenderer extends EntityRenderer<OctoMountingEntity> {
    public OctoMountingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull OctoMountingEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "textures/entity/empty.png");
    }
} 