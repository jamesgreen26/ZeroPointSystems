package g_mungus.zps.commands.lang.providers;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.commands.lang.converters.Converter;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;

public interface Provider<T> {
    T get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;

    List<Converter<T, ?>> getConverters();
}
