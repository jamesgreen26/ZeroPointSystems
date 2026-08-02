package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api_impl.arguments.OverloadedExecutorArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CommandTreeBuilderTest {
    private static final ResourceLocation INT_KEY = ResourceLocation.parse("zps:int");
    private static final ResourceLocation BLOCK_A = ResourceLocation.parse("zps:block_a");
    private static final ResourceLocation BLOCK_B = ResourceLocation.parse("zps:block_b");

    @AfterEach
    void cleanup() {
        Registry.clear();
    }

    @Test
    public void duplicateExecutorNamesBuildSingleOverloadedLiteralBranch() {
        Registry.register(executorFor(BLOCK_A));
        Registry.register(executorFor(BLOCK_B));

        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithInternalRoots();
        new CommandTreeBuilder(dispatcher).buildExecutors();

        CommandNode<CommandSourceStack> executorRoot = dispatcher.getRoot()
                .getChild(ZPSCommands.Paths.INTERNAL)
                .getChild(ZPSCommands.Paths.EXECUTORS);
        CommandNode<CommandSourceStack> duplicateLiteral = executorRoot.getChild("set_mode");

        assertNotNull(duplicateLiteral);
        assertEquals(1, duplicateLiteral.getChildren().size(),
                "Duplicate executor names should produce one visible command branch");

        CommandNode<CommandSourceStack> argument = duplicateLiteral.getChildren().iterator().next();
        assertInstanceOf(ArgumentCommandNode.class, argument);
        assertInstanceOf(OverloadedExecutorArgumentType.class,
                ((ArgumentCommandNode<?, ?>) argument).getType());
    }

    private static ScriptExecutor<Integer, Integer> executorFor(ResourceLocation block) {
        return ScriptExecutor.simpleWithBlocks(
                "set_mode",
                Integer.class,
                INT_KEY,
                IntegerArgumentType.integer(),
                (value, context) -> value,
                Set.of(block)
        );
    }

    private static CommandDispatcher<CommandSourceStack> dispatcherWithInternalRoots() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(
                Commands.literal(ZPSCommands.Paths.INTERNAL)
                        .then(Commands.literal(ZPSCommands.Paths.EXECUTORS))
                        .then(Commands.literal(ZPSCommands.Paths.MAPPERS))
                        .then(Commands.literal(ZPSCommands.Paths.GETTERS))
        );
        return dispatcher;
    }
}
