package g_mungus.zps.commands;

import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZPSScriptCommandSource implements CommandSource {
    private final @Nullable CommandSource delegate;
    private BlockPos blockPos = new BlockPos(0, 0, 0);

    public ZPSScriptCommandSource(@Nullable CommandSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendSystemMessage(@NotNull Component arg) {
        if (delegate != null) {
            delegate.sendSystemMessage(arg);
        }
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    public void setPos(BlockPos pos) {
        this.blockPos = pos;
    }

    public BlockPos getPos() {
        return this.blockPos;
    }
}
