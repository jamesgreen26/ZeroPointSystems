package g_mungus.zps.commands.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A block a script command can be aimed at. The server reads one where a command runs; a script
 * editor is sent those its script can reach, to suggest only what applies to one of them. The
 * block comes along because a client may not have it loaded.
 */
public record ScriptTarget(BlockPos pos, ResourceLocation block) {

    public static void write(FriendlyByteBuf buffer, ScriptTarget target) {
        buffer.writeBlockPos(target.pos);
        buffer.writeResourceLocation(target.block);
    }

    public static ScriptTarget read(FriendlyByteBuf buffer) {
        return new ScriptTarget(buffer.readBlockPos(), buffer.readResourceLocation());
    }

    /** The block at {@code pos}. */
    public static ScriptTarget at(Level level, BlockPos pos) {
        return new ScriptTarget(pos.immutable(), BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }
}
