package g_mungus.zps.commands.api_impl;

import g_mungus.zps.commands.api.ScriptContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ZPSScriptCommandSource implements CommandSource {
    private final @Nullable CommandSource delegate;
    private BlockPos blockPos = new BlockPos(0, 0, 0);
    public Object predicateValue = null;
    public Object pendingResult = null;
    public PredicateType predicate = PredicateType.NONE;
    public Supplier<Integer> execute = null;
    public Class<?> executeType = null;
    private Map<String, BlockPos> availableAddresses = Collections.emptyMap();

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

    public void setAvailableAddresses(Map<String, BlockPos> availableAddresses) {
        this.availableAddresses = new HashMap<>(availableAddresses);
    }

    public Collection<String> getAvailableAddressNames() {
        return availableAddresses.keySet();
    }

    public @Nullable BlockPos resolveAddress(String name) {
        return availableAddresses.get(name);
    }

    @ApiStatus.Internal
    public enum PredicateType {
        NONE, IF, UNLESS;

        public boolean test(Object bool) {
            return switch (this) {
                case NONE -> true;
                case IF -> {
                    assert bool instanceof Boolean;
                    yield (Boolean) bool;
                }
                case UNLESS -> {
                    assert bool instanceof Boolean;
                    yield !((Boolean) bool);
                }
            };
        }

        public PredicateType cycle() {
            return switch (this) {
                case NONE -> NONE;
                case IF -> UNLESS;
                case UNLESS -> IF;
            };
        }
    }
}
