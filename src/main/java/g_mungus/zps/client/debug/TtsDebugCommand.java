package g_mungus.zps.client.debug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import g_mungus.zps.client.tts.TtsSoundsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class TtsDebugCommand {
    private static final RequiredArgumentBuilder<CommandSourceStack, String> textArg =
            Commands.argument("text", StringArgumentType.greedyString())
                    .executes(context -> {
                        String text = StringArgumentType.getString(context, "text");

                        assert Minecraft.getInstance().player != null;
                        TtsSoundsManager.speakTextAt(Minecraft.getInstance().player.blockPosition(), text);

                        return 1;
                    });

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .then(Commands.literal("tts")
                            .then(textArg));
}