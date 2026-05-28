package g_mungus.zps.client.renderer;

import g_mungus.zps.ZPSMod;
import net.createmod.catnip.render.BindableTexture;
import net.minecraft.resources.ResourceLocation;

public enum ZPSSpecialTextures implements BindableTexture {
    CHECKERED("checkerboard.png"),
    HIGHLIGHT_CHECKERED("highlighted_checkerboard.png");

    private static final String ASSET_PATH = "textures/special/";
    private final ResourceLocation location;

    ZPSSpecialTextures(String filename) {
        this.location = ZPSMod.resource(ASSET_PATH + filename);
    }

    @Override
    public ResourceLocation getLocation() {
        return location;
    }
}
