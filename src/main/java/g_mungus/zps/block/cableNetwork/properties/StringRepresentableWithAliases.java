package g_mungus.zps.block.cableNetwork.properties;

import net.minecraft.util.StringRepresentable;

import java.util.Set;

public interface StringRepresentableWithAliases extends StringRepresentable {
    Set<String> getAliases();
}
