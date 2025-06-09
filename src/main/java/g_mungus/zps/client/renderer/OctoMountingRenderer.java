package g_mungus.zps.client.renderer;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.OctoMountingEntity;
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