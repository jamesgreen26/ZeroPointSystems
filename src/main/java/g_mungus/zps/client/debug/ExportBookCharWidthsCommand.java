package g_mungus.zps.client.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExportBookCharWidthsCommand {
    private static final String ARG_START = "start";
    private static final String ARG_END = "end";

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .then(Commands.literal("export_book_char_widths")
                            .then(Commands.argument(ARG_START, IntegerArgumentType.integer(0, Character.MAX_CODE_POINT))
                                    .then(Commands.argument(ARG_END, IntegerArgumentType.integer(0, Character.MAX_CODE_POINT))
                                            .executes(context -> export(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, ARG_START),
                                                    IntegerArgumentType.getInteger(context, ARG_END)
                                            )))));

    private static int export(CommandSourceStack source, int start, int end) {
        if (end < start) {
            source.sendFailure(Component.literal("end must be greater than or equal to start"));
            return 0;
        }

        Path outputDir = FMLPaths.GAMEDIR.get().resolve("debug");
        Path output = outputDir.resolve(String.format("book_char_widths_%04X_%04X.txt", start, end));

        try {
            Files.createDirectories(outputDir);
            Files.writeString(output, buildJavaSnippet(start, end));
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to export char widths: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Exported book char widths to " + output), false);
        return 1;
    }

    private static String buildJavaSnippet(int start, int end) {
        Minecraft minecraft = Minecraft.getInstance();
        StringBuilder builder = new StringBuilder();
        builder.append("private static final int MIN_CODE_POINT = 0x")
                .append(String.format("%04X", start))
                .append(";\n");
        builder.append("private static final int MAX_CODE_POINT = 0x")
                .append(String.format("%04X", end))
                .append(";\n");
        builder.append("private static final int[] WIDTHS_X256 = {\n");

        int column = 0;
        for (int codePoint = start; codePoint <= end; codePoint++) {
            int widthX256 = Math.round(minecraft.font.getSplitter()
                    .stringWidth(new String(Character.toChars(codePoint))) * 256.0f);
            if (column == 0) {
                builder.append("        ");
            }
            builder.append(String.format("%4d", widthX256));
            if (codePoint < end) {
                builder.append(", ");
            }
            column++;
            if (column == 16 && codePoint < end) {
                builder.append('\n');
                column = 0;
            }
        }

        if (column != 0) {
            builder.append('\n');
        }
        builder.append("};\n");
        return builder.toString();
    }
}
