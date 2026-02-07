package g_mungus.zps.commands.exceptions;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class UnsupportedOperationException extends ScriptException {
    public UnsupportedOperationException(Component arg) {
        super(arg);
    }

    @SuppressWarnings("deprecation")
    public static UnsupportedOperationException build(String operation, Block block) {
        return new UnsupportedOperationException(Component.literal("Operation unsuccessful: cannot execute \"" + operation + "\" for block " + block.builtInRegistryHolder().key().location()));
    }
}
