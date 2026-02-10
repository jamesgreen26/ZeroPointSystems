package g_mungus.zps.commands.lang.providers;

import com.mojang.brigadier.context.CommandContext;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.lang.converters.Converter;
import g_mungus.zps.commands.lang.converters.ConverterRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public class BuiltinProviders {
    public static void register() {
        ProviderRegistry.register(
                BlockPos.class,
                "POS",
                new Provider<>() {
                    @Override
                    public BlockPos get(CommandContext<CommandSourceStack> ctx) {
                        return ZPSCommands.getPosition(ctx);
                    }

                    @Override
                    public List<Converter<BlockPos, ?>> getConverters() {
                        Map<String, Converter<BlockPos, Integer>> possibilities = ConverterRegistry.get(BlockPos.class, Integer.class);
                        return List.of(
                                possibilities.get("X"),
                                possibilities.get("Y"),
                                possibilities.get("Z")
                        );
                    }
                }
        );

    }
}
