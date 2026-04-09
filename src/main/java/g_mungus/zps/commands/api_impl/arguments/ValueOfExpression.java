package g_mungus.zps.commands.api_impl.arguments;

import com.mojang.brigadier.CommandDispatcher;
import g_mungus.zps.commands.api_impl.ValueOfDispatchers;
import g_mungus.zps.commands.api_impl.ZPSScriptCommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record ValueOfExpression<A>(String innerExpression, ResourceLocation targetTypeKey) {
    @SuppressWarnings("unchecked")
    public A evaluate(CommandSourceStack outerStack, BlockPos pos) {
        CommandDispatcher<CommandSourceStack> inner = ValueOfDispatchers.get(targetTypeKey);
        if (inner == null) {
            throw new RuntimeException("No value_of dispatcher registered for type: " + targetTypeKey);
        }
        ZPSScriptCommandSource innerSource = new ZPSScriptCommandSource(outerStack.source);
        innerSource.setPos(pos);
        CommandSourceStack innerStack = outerStack.withSource(innerSource);
        try {
            inner.execute(innerExpression + " " + ValueOfDispatchers.TERMINAL_LITERAL, innerStack);
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating value_of(" + innerExpression + "): " + e.getMessage(), e);
        }
        return (A) innerSource.pendingResult;
    }
}
