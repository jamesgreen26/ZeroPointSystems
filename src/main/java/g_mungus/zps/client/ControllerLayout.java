package g_mungus.zps.client;

import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.blockentity.DodecaControllerBlockEntity;

/**
 * How a controller's channels are laid out in its key screen.
 *
 * <p>Channels come in sides of four: A-D, E-H and I-L each leave the block from one side. They are
 * shown as the rider sees them, facing the block — so the block's clockwise side is on their left —
 * and coloured as the Dense Cable Separator colours its four channels.
 */
public final class ControllerLayout {
    private ControllerLayout() {}

    public static final int CHANNELS_PER_SIDE = 4;

    /** The Dense Cable Separator's channel colours, in channel order: green, blue, red, purple. */
    public static final int[] CHANNEL_COLOURS = {0xFF0C871F, 0xFF115E92, 0xFF870C28, 0xFF5D26A8};

    /** The lang key suffix of each side, in channel order. */
    public static String[] sides(ControllerBlockEntity<?> controller) {
        return controller instanceof DodecaControllerBlockEntity
                ? new String[]{"left", "right", "back"}
                : new String[]{"down", "back"};
    }

    /** Which column, from the rider's left, each side is shown in, in channel order: back sits between left and right. */
    public static int[] sideColumns(ControllerBlockEntity<?> controller) {
        return controller instanceof DodecaControllerBlockEntity ? new int[]{0, 2, 1} : new int[]{0, 1};
    }
}
