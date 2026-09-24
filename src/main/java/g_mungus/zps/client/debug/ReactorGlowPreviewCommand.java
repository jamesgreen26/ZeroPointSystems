package g_mungus.zps.client.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.zps.client.reactor.ReactorGlowPreviews;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /zps_debug reactor_glow box <x> <y> <z> [heat]} draws a reactor glow of that size a few
 * blocks ahead of the player, with no reactor behind it; {@code /zps_debug reactor_glow clear}
 * takes them all away. Heat is over
 * ignition: 1 is just lit, 2 and up is blue.
 */
public class ReactorGlowPreviewCommand {
    private static final int MAX_SIZE = 16;
    private static final int AHEAD = 3;

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND = Commands
            .literal("zps_debug").then(Commands.literal("reactor_glow")
                    .then(Commands.literal("clear").executes(context -> {
                        int cleared = ReactorGlowPreviews.clear();
                        context.getSource().sendSuccess(() -> Component.literal("Cleared " + cleared + " glow preview(s)"), false);
                        return cleared;
                    }))
                    .then(Commands.literal("box")
                            .then(Commands.argument("x", IntegerArgumentType.integer(1, MAX_SIZE))
                                    .then(Commands.argument("y", IntegerArgumentType.integer(1, MAX_SIZE))
                                            .then(Commands.argument("z", IntegerArgumentType.integer(1, MAX_SIZE))
                                                    .executes(context -> add(context, 1.2f))
                                                    .then(Commands.argument("heat", FloatArgumentType.floatArg(0f, 4f))
                                                            .executes(context -> add(context, FloatArgumentType.getFloat(context, "heat")))))))));

    private static int add(CommandContext<CommandSourceStack> context, float heat) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        BlockPos origin = player.blockPosition().relative(player.getDirection(), AHEAD);
        boolean added = ReactorGlowPreviews.addBox(origin,
                IntegerArgumentType.getInteger(context, "x"),
                IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"), heat);
        if (!added) {
            context.getSource().sendFailure(Component.literal("Could not build a glow for that size"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Glow preview at " + origin.toShortString() + ", heat " + heat), false);
        return 1;
    }
}
