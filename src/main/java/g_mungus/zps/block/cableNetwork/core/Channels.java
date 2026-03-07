package g_mungus.zps.block.cableNetwork.core;

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

    public static final int DOD_A = 16;
    public static final int DOD_B = 17;
    public static final int DOD_C = 18;
    public static final int DOD_D = 19;
    public static final int DOD_E = 20;
    public static final int DOD_F = 21;
    public static final int DOD_G = 22;
    public static final int DOD_H = 23;
    public static final int DOD_I = 24;
    public static final int DOD_J = 25;
    public static final int DOD_K = 26;
    public static final int DOD_L = 27;

    public static final int PAIR_A = 14;
    public static final int PAIR_B = 15;

    public static int getInitialChannel(int channelCount) {
        switch (channelCount) {
            case 2 -> { return PAIR_A; }
            case 4 -> { return QUAD_1; }
            case 8 -> { return OCT_A; }
            case 12 -> { return DOD_A; }
            default -> { return MAIN; }
        }
    }

    public static int getFinalChannel(int channelCount) {
        switch (channelCount) {
            case 2 -> { return PAIR_B; }
            case 4 -> { return QUAD_4; }
            case 8 -> { return OCT_H; }
            case 12 -> { return DOD_L; }
            default -> { return MAIN; }
        }
    }

    public static int toQuad(int in) {
        if (in >= QUAD_1 && in <= QUAD_4) {
            return in;
        }
        if (in >= OCT_A && in <= OCT_H) {
            return ((in - OCT_A) % 4) + QUAD_1;
        }
        if (in >= DOD_A && in <= DOD_L) {
            return ((in - DOD_A) % 4) + QUAD_1;
        }
        return ((in - QUAD_1) % 4 + 4) % 4 + QUAD_1;
    }
}
