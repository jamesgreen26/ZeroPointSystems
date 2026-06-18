package g_mungus.zps.commands.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class SetHeldItemEnergyCommand {
    private static final String ARG_ENERGY = "energy";

    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND =
            Commands.literal("zps_debug")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("set_held_item_energy")
                            .then(Commands.argument(ARG_ENERGY, IntegerArgumentType.integer(0))
                                    .executes(context -> setEnergy(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, ARG_ENERGY)
                                    ))));

    private static int setEnergy(CommandSourceStack source, int targetEnergy) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("No item is held in the main hand"));
            return 0;
        }

        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null) {
            source.sendFailure(Component.literal("Held item does not expose energy storage"));
            return 0;
        }
        return setEnergy(source, stack, energy, targetEnergy);
    }

    private static int setEnergy(CommandSourceStack source, ItemStack stack, IEnergyStorage energy, int targetEnergy) {
        int clampedTarget = Math.min(targetEnergy, energy.getMaxEnergyStored());
        int current = energy.getEnergyStored();

        if (clampedTarget > current) {
            energy.receiveEnergy(clampedTarget - current, false);
        } else if (clampedTarget < current) {
            energy.extractEnergy(current - clampedTarget, false);
        }

        int stored = energy.getEnergyStored();
        source.sendSuccess(() -> Component.literal("Set " + stack.getHoverName().getString()
                + " energy to " + stored + " / " + energy.getMaxEnergyStored() + " FE"), false);
        return 1;
    }
}
