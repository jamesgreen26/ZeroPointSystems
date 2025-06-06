package g_mungus.block.cableNetwork.core;

public class Channels {
    public static final int MAIN = 0;

    public static final int QUAD_1 = 1;
    public static final int QUAD_2 = 2;
    public static final int QUAD_3 = 3;
    public static final int QUAD_4 = 4;

    public static final int OCT_A = 5;
    public static final int OCT_B = 6;
    public static final int OCT_C = 7;
    public static final int OCT_D = 8;
    public static final int OCT_E = 9;
    public static final int OCT_F = 10;
    public static final int OCT_G = 11;
    public static final int OCT_H = 12;

    public static int getInitialChannel(int channelCount) {
        switch (channelCount) {
            default -> { return MAIN; }
            case 4 -> { return QUAD_1; }
            case 8 -> { return OCT_A; }
        }
    }

    public static int getFinalChannel(int channelCount) {
        switch (channelCount) {
            default -> { return MAIN; }
            case 4 -> { return QUAD_4; }
            case 8 -> { return OCT_H; }
        }
    }
}
