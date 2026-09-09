package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.ScriptGetter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Explains, in a player's terms, why a {@code value_of(...)} expression could not be read for the
 * type its command wanted.
 *
 * <p>Each value_of target type has its own dispatcher whose nodes are the getters and mappers
 * that can reach that type; an expression is only complete once it lands on the target's
 * {@code have-<type>} node. Parsing the expression on its own, without the terminal literal,
 * shows how far it got and which type it stopped at, which is what the message is built from.
 */
public final class ValueOfDiagnostics {

    private static final String HAVE_PREFIX = "have-";

    private ValueOfDiagnostics() {}

    /**
     * @param expression the text between the parentheses
     * @param targetType the type the surrounding command needs
     * @param consumer   the command or mapper that needs it, or null when not known
     */
    public static String explain(String expression, ResourceLocation targetType, @Nullable String consumer,
                                 CommandSourceStack stack) {
        String token = "value_of(" + expression + ")";
        String needs = (consumer != null ? consumer : "the command") + " needs " + TypeKeys.name(targetType);
        String trimmed = expression.strip();
        if (trimmed.isEmpty()) {
            return token + " is empty, and " + needs;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = ValueOfDispatchers.get(targetType);
        if (dispatcher == null) {
            return token + " cannot be used here: nothing can be turned into " + TypeKeys.name(targetType);
        }

        ParseResults<CommandSourceStack> parse = dispatcher.parse(trimmed, stack);
        // Each redirect opens a child context; one that failed to parse anything is left empty,
        // so the last context with nodes is where the expression actually got to.
        List<ParsedCommandNode<CommandSourceStack>> nodes = List.of();
        for (CommandContextBuilder<CommandSourceStack> context = parse.getContext();
             context != null; context = context.getChild()) {
            if (!context.getNodes().isEmpty()) {
                nodes = context.getNodes();
            }
        }
        int consumed = parse.getReader().getCursor();
        String leftover = trimmed.substring(Math.min(trimmed.length(), consumed)).strip();

        if (nodes.isEmpty()) {
            String first = firstWord(trimmed);
            ScriptGetter<?> getter = findGetter(first);
            if (getter != null) {
                return token + " gives " + TypeKeys.name(getter.outputKey())
                        + ", which cannot be turned into " + TypeKeys.name(targetType)
                        + (consumer != null ? " for " + consumer : "");
            }
            return "'" + first + "' is not a known value in " + token;
        }

        CommandNode<CommandSourceStack> last = nodes.getLast().getNode();
        CommandNode<CommandSourceStack> reached = last.getRedirect();
        if (reached == null) {
            // A mapper literal parsed but its argument did not follow: the expression stops short.
            if (!leftover.isEmpty()) {
                return "In " + token + ", '" + firstWord(leftover) + "' is not a valid value for '"
                        + last.getName() + "'";
            }
            return token + " is incomplete: '" + last.getName() + "' needs a value after it";
        }

        ResourceLocation reachedType = typeOf(reached);
        String reachedName = reachedType != null ? TypeKeys.name(reachedType) : "'" + last.getName() + "'";
        if (!leftover.isEmpty()) {
            return "In " + token + ", '" + firstWord(leftover) + "' cannot follow " + reachedName;
        }
        if (reachedType != null && reachedType.equals(targetType)) {
            // Would have run; whatever went wrong is not a type problem.
            return token + " could not be evaluated";
        }
        return token + " gives " + reachedName + ", but " + needs;
    }

    /** The type a {@code have-<type>} node stands for, or null for any other node. */
    private static @Nullable ResourceLocation typeOf(CommandNode<CommandSourceStack> node) {
        String name = node.getName();
        if (!name.startsWith(HAVE_PREFIX)) {
            return null;
        }
        return ResourceLocation.tryParse(name.substring(HAVE_PREFIX.length()));
    }

    private static @Nullable ScriptGetter<?> findGetter(String name) {
        for (ScriptGetter<?> getter : Registry.GETTERS) {
            if (getter.displayName().equals(name)) {
                return getter;
            }
        }
        return null;
    }

    private static String firstWord(String text) {
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }
}
