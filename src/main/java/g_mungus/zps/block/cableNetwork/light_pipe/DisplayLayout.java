package g_mungus.zps.block.cableNetwork.light_pipe;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a text display block sits in its merged display: the display's size in
 * blocks, and this block's column (0 at the viewer's left) and row (0 at the top).
 * The first nine keep their original names so displays in old worlds and
 * structures still load merged.
 */
public enum DisplayLayout implements StringRepresentable {
    SINGLE_1x1("single", 1, 1, 0, 0),
    LEFT_2x1("left", 2, 1, 0, 0),
    RIGHT_2x1("right", 2, 1, 1, 0),
    TOP_1x2("top", 1, 2, 0, 0),
    BOTTOM_1x2("bottom", 1, 2, 0, 1),
    TOP_LEFT_2x2("top_left", 2, 2, 0, 0),
    TOP_RIGHT_2x2("top_right", 2, 2, 1, 0),
    BOTTOM_LEFT_2x2("bottom_left", 2, 2, 0, 1),
    BOTTOM_RIGHT_2x2("bottom_right", 2, 2, 1, 1),
    GRID_3x1_0_0("3x1_0_0", 3, 1, 0, 0),
    GRID_3x1_1_0("3x1_1_0", 3, 1, 1, 0),
    GRID_3x1_2_0("3x1_2_0", 3, 1, 2, 0),
    GRID_1x3_0_0("1x3_0_0", 1, 3, 0, 0),
    GRID_1x3_0_1("1x3_0_1", 1, 3, 0, 1),
    GRID_1x3_0_2("1x3_0_2", 1, 3, 0, 2),
    GRID_3x2_0_0("3x2_0_0", 3, 2, 0, 0),
    GRID_3x2_1_0("3x2_1_0", 3, 2, 1, 0),
    GRID_3x2_2_0("3x2_2_0", 3, 2, 2, 0),
    GRID_3x2_0_1("3x2_0_1", 3, 2, 0, 1),
    GRID_3x2_1_1("3x2_1_1", 3, 2, 1, 1),
    GRID_3x2_2_1("3x2_2_1", 3, 2, 2, 1),
    GRID_2x3_0_0("2x3_0_0", 2, 3, 0, 0),
    GRID_2x3_1_0("2x3_1_0", 2, 3, 1, 0),
    GRID_2x3_0_1("2x3_0_1", 2, 3, 0, 1),
    GRID_2x3_1_1("2x3_1_1", 2, 3, 1, 1),
    GRID_2x3_0_2("2x3_0_2", 2, 3, 0, 2),
    GRID_2x3_1_2("2x3_1_2", 2, 3, 1, 2),
    GRID_3x3_0_0("3x3_0_0", 3, 3, 0, 0),
    GRID_3x3_1_0("3x3_1_0", 3, 3, 1, 0),
    GRID_3x3_2_0("3x3_2_0", 3, 3, 2, 0),
    GRID_3x3_0_1("3x3_0_1", 3, 3, 0, 1),
    GRID_3x3_1_1("3x3_1_1", 3, 3, 1, 1),
    GRID_3x3_2_1("3x3_2_1", 3, 3, 2, 1),
    GRID_3x3_0_2("3x3_0_2", 3, 3, 0, 2),
    GRID_3x3_1_2("3x3_1_2", 3, 3, 1, 2),
    GRID_3x3_2_2("3x3_2_2", 3, 3, 2, 2);

    DisplayLayout(String serializedName, int width, int height, int column, int row) {
        this.serializedName = serializedName;
        this.width = width;
        this.height = height;
        this.column = column;
        this.row = row;
    }

    public final String serializedName;
    public final int width;
    public final int height;
    public final int column;
    public final int row;

    /** The layout for that cell of a display that size, or null past {@link TextDisplayBlock#MAX_DISPLAY_SIZE}. */
    public static @Nullable DisplayLayout of(int width, int height, int column, int row) {
        for (DisplayLayout layout : values()) {
            if (layout.width == width && layout.height == height && layout.column == column && layout.row == row) {
                return layout;
            }
        }
        return null;
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }
}
