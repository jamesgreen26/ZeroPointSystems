package g_mungus.zps.block.cableNetwork.light_pipe;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public enum DisplayLayout implements StringRepresentable {
    SINGLE_1x1("single", 1, 1, 14, 14, 12, 12, 1, 1, 2, 2),
    LEFT_2x1("left", 2, 1, 15, 14, 14, 12, 1, 1, 2, 2),
    RIGHT_2x1("right", 2, 1, 15, 14, 14, 12, 0, 1, 0, 2),
    TOP_1x2("top", 1, 2, 14, 15, 12, 14, 1, 1, 2, 2),
    BOTTOM_1x2("bottom", 1, 2, 14, 15, 12, 14, 1, 0, 2, 0),
    TOP_LEFT_2x2("top_left", 2, 2,15, 15, 14, 14, 1, 1, 2, 2),
    TOP_RIGHT_2x2("top_right", 2, 2,15, 15, 14, 14, 0, 1, 0, 2),
    BOTTOM_LEFT_2x2("bottom_left", 2, 2,15, 15, 14, 14, 1, 0, 2, 0),
    BOTTOM_RIGHT_2x2("bottom_right", 2, 2,15, 15, 14, 14, 0, 0, 0, 0);

    DisplayLayout(
            String serializedName,
            int blockCountX, int blockCountY,
            int videoResolutionX, int videoResolutionY,
            int textResolutionX, int textResolutionY,
            int videoOffsetX, int videoOffsetY,
            int textOffsetX, int textOffsetY
    ) {
        this.serializedName = serializedName;
        this.blockDimensions = new Vector2i(blockCountX, blockCountY);
        this.videoResolution = new Vector2i(videoResolutionX, videoResolutionY);
        this.textResolution = new Vector2i(textResolutionX, textResolutionY);
        this.videoOffset = new Vector2i(videoOffsetX, videoOffsetY);
        this.textOffset = new Vector2i(textOffsetX, textOffsetY);
    }

    public final String serializedName;
    public final Vector2ic blockDimensions;
    public final Vector2ic videoResolution;
    public final Vector2ic textResolution;
    public final Vector2ic videoOffset;
    public final Vector2ic textOffset;

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }
}
