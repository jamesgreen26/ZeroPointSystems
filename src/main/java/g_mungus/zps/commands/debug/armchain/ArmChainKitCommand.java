package g_mungus.zps.commands.debug.armchain;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /zps_debug arm_chain_kit} builds an {@link ArmChainKit} at the player's feet, growing the way they face.
 * {@code /zps_debug arm_chain_kit save} builds it facing east and writes it out as the {@code zps:arm_chain_kit}
 * template, into the world's generated structures, to copy into the mod's resources.
 */
public class ArmChainKitCommand {

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("arm_chain_kit")
                            .executes(context -> build(context.getSource()))
                            .then(Commands.literal("save")
                                    .executes(context -> save(context.getSource()))));

    private static int build(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ArmChainKit.build(player.serverLevel(), player.blockPosition(), player.getDirection());
        source.sendSuccess(() -> Component.literal("Arm chain kit built; flip the lever to start it"), false);
        return 1;
    }

    private static int save(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos corner = player.blockPosition();
        CompoundTag tag = ArmChainKit.capture(player.serverLevel(), corner);
        Path file = source.getServer().getWorldPath(LevelResource.GENERATED_DIR).normalize()
                .resolve(ArmChainKit.TEMPLATE.getNamespace()).resolve("structures")
                .resolve(ArmChainKit.TEMPLATE.getPath() + ".nbt");
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Arm chain kit saved to " + file), false);
        return 1;
    }
}
