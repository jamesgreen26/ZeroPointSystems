package g_mungus.zps.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.ZPSMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.redstone.NeighborUpdater;

import java.util.List;

public class SetRedstoneCommand {
    public static final ResourceLocation DATA_LOCATION = ZPSMod.resource("redstone");

    public static LiteralArgumentBuilder<CommandSourceStack> build(ZPSRegisterScriptCommandEvent event) {
        return Commands.literal("SET_REDSTONE").then(
                Commands.argument("power", IntegerArgumentType.integer(0, 15))
                        .executes(context -> {
                            List<BlockInWorld> blocks = event.getBlocks(context);
                            for (var block : blocks) {
                                setRedstone(context.getSource().getLevel(), block.getPos(), IntegerArgumentType.getInteger(context, "power"));
                            }
                            return 1;
                        }));
    }

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
