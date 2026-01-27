package g_mungus.zps.blockentity.light_pipe;

import java.util.List;

public interface HasFrequency {
    void cycleFrequencies();

    List<Integer> FREQUENCIES = List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80);
}
