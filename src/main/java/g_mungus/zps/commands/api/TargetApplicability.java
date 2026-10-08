package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.Applicability;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * Which targets a script node is for. An executor among several of the same name runs for the
 * target it applies to; otherwise this only steers what is suggested, so a node still has to cope
 * with being pointed at anything.
 *
 * <p>Clients are sent these with the script tree, so each kind has a {@link Type}, registered on
 * both sides through {@link RegisterScriptTypesEvent#registerApplicability}.
 */
public interface TargetApplicability extends Applicability {

    /** @param level the level the target is in, on whichever side is asking */
    boolean appliesTo(Level level, ScriptTarget target);

    default boolean appliesToAnyTarget(Level level, Collection<ScriptTarget> targets) {
        return targets.stream().anyMatch(target -> appliesTo(level, target));
    }

    Type<?> type();

    /**
     * A kind of applicability, as the script tree packet names and writes it.
     *
     * @param codec what the server writes and the client reads back. It may write less than the
     *              server holds, such as block tags already opened into blocks.
     */
    record Type<T extends TargetApplicability>(ResourceLocation id, StreamCodec<ByteBuf, T> codec) {
    }
}
