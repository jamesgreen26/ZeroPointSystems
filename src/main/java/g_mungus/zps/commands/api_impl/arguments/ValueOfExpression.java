package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.zps.commands.api_impl.ValueOfDiagnostics;
import g_mungus.zps.commands.api_impl.ValueOfDispatchers;
import g_mungus.zps.commands.api_impl.ZPSScriptCommandSource;
import g_mungus.zps.commands.api_impl.exceptions.ValueOfEvaluationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ValueOfExpression<A>(String innerExpression, ResourceLocation targetTypeKey) {
    public A evaluate(CommandSourceStack outerStack, BlockPos pos) {
        return evaluate(outerStack, pos, null);
    }

    /**
     * @param consumer the command or mapper this value is for, named in the failure message when
     *                 the expression does not yield the type it needs
     */
    @SuppressWarnings("unchecked")
    public A evaluate(CommandSourceStack outerStack, BlockPos pos, @Nullable String consumer) {
        CommandDispatcher<CommandSourceStack> inner = ValueOfDispatchers.get(targetTypeKey);
        if (inner == null) {
            throw new RuntimeException("No value_of dispatcher registered for type: " + targetTypeKey);
        }
        ZPSScriptCommandSource innerSource = new ZPSScriptCommandSource(outerStack.source);
        innerSource.setPos(pos);
        CommandSourceStack innerStack = outerStack.withSource(innerSource);
        try {
            inner.execute(innerExpression + " " + ValueOfDispatchers.TERMINAL_LITERAL, innerStack);
        } catch (CommandSyntaxException e) {
            // The expression did not read as the type wanted here; say so in the player's terms.
            throw new ValueOfEvaluationException(innerExpression,
                    ValueOfDiagnostics.explain(innerExpression, targetTypeKey, consumer, innerStack), e);
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating value_of(" + innerExpression + "): " + e.getMessage(), e);
        }
        return (A) innerSource.pendingResult;
    }
}
