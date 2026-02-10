package g_mungus.zps.commands.api_impl;

import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ZPSScriptCommandSource implements CommandSource {
    private final @Nullable CommandSource delegate;
    private BlockPos blockPos = new BlockPos(0, 0, 0);
    public Object value = null;
    public Class<?> desiredOutputType = null;
    public Consumer<Object> execute = null;

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
