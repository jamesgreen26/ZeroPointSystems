package g_mungus.zps.manual;

import net.minecraft.network.chat.Component;

public record ManualSection(
    String id,
    String documentPath,
    Component title
) {

}
