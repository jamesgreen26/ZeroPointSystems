package g_mungus.zps.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.ZPSMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.redstone.NeighborUpdater;

public class SetRedstoneCommand {
    public static final ResourceLocation DATA_LOCATION = ZPSMod.resource("redstone");

    public static final CommandNode<CommandSourceStack> COMMAND = Commands.literal("SET_REDSTONE").then(
            Commands.argument("power", IntegerArgumentType.integer(0, 15))
                    .executes(context -> {
                        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");
                        setRedstone(context.getSource().getLevel(), pos, IntegerArgumentType.getInteger(context, "power"));
                        return 1;
                    })).build();

    public static int getRedstonePowerAt(ServerLevel level, BlockPos pos) {
        CompoundTag root = level.getServer().getCommandStorage().get(DATA_LOCATION);
        String dimension = getDimensionKey(level);
        if (!root.contains(dimension)) {
            return 0;
        } else {
            CompoundTag tag = root.getCompound(dimension);
            String key = Long.toString(pos.asLong());
            return tag.getInt(key);
        }
    }

    private static void setRedstone(ServerLevel level, BlockPos pos, int power) {
        CompoundTag root = level.getServer().getCommandStorage().get(DATA_LOCATION);
        String dimension = getDimensionKey(level);
        CompoundTag tag;
        if (!root.contains(dimension)) {
            tag = new CompoundTag();
            root.put(dimension, tag);
        } else {
            tag = root.getCompound(dimension);
        }

        String key = Long.toString(pos.asLong());
        tag.putInt(key, power);
        level.getServer().getCommandStorage().set(DATA_LOCATION, root);

        for (var dir : NeighborUpdater.UPDATE_ORDER) {
            BlockPos neighbor = pos.offset(dir.getNormal());
            level.neighborChanged(pos, level.getBlockState(neighbor).getBlock(), neighbor);
        }
    }

    private static String getDimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

}
