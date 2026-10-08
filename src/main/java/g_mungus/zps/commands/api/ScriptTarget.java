package g_mungus.zps.commands.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A block a script command can be aimed at. The server reads one where a command runs; a script
 * editor is sent those its script can reach, to suggest only what applies to one of them. The
 * block comes along because a client may not have it loaded.
 */
public record ScriptTarget(BlockPos pos, ResourceLocation block) {

    public static final StreamCodec<ByteBuf, ScriptTarget> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScriptTarget::pos,
            ResourceLocation.STREAM_CODEC, ScriptTarget::block,
            ScriptTarget::new);

    /** The block at {@code pos}. */
    public static ScriptTarget at(Level level, BlockPos pos) {
        return new ScriptTarget(pos.immutable(), BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }
}
