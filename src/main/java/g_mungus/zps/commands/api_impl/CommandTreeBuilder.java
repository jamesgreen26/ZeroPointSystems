package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.MappedArgumentType;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.commands.api_impl.arguments.ZPSArgument;
import g_mungus.zps.commands.api_impl.arguments.ZPSLiteral;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CommandTreeBuilder {

    public final CommandDispatcher<CommandSourceStack> dispatcher;

    CommandNode<CommandSourceStack> executors;
    CommandNode<CommandSourceStack> mappers;
    CommandNode<CommandSourceStack> getters;

    private final MapperGraph mapperGraph;

    public CommandTreeBuilder(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;

        getters = dispatcher.getRoot()
                .getChild(ZPSCommands.Paths.INTERNAL)
                .getChild(ZPSCommands.Paths.GETTERS);

        mappers = dispatcher.getRoot()
                .getChild(ZPSCommands.Paths.INTERNAL)
                .getChild(ZPSCommands.Paths.MAPPERS);

        executors = dispatcher.getRoot()
                .getChild(ZPSCommands.Paths.INTERNAL)
                .getChild(ZPSCommands.Paths.EXECUTORS);

        this.mapperGraph = new MapperGraph();
        Registry.MAPPERS.forEach(mapperGraph::addMapper);
    }

    public void buildGetters() {

        Set<ResourceLocation> allOutputs = Registry.MAPPERS.stream().map(ScriptMapper::outputKey).collect(Collectors.toSet());

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                for (var getter : Registry.GETTERS) {
                    if (getter.outputKey().equals(mapper.inputKey())) {
                        boolean done = output.equals(getter.outputKey());

                        CommandNode<CommandSourceStack> destination = done ? executors : mappers.getChild("have-" + getter.outputKey() + "-need-" + output);
                        getters.addChild(new ZPSLiteral.Builder<CommandSourceStack>("need-" + output).then(new ZPSLiteral.Builder<CommandSourceStack>(getter.displayName()).forward(destination, context -> {
                            if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                source.value = getter.function().apply(new ScriptContextImpl(context, source.getPos(), context.getSource().getLevel()));

                                if (source.execute != null && source.value.getClass().equals(source.desiredOutputType)) {
                                    source.execute.accept(source.value);
                                }
                            }
                            return List.of(context.getSource());
                        }, false)).build());
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void buildMappers() {

        Set<ResourceLocation> allOutputs = Registry.MAPPERS.stream().map(ScriptMapper::outputKey).collect(Collectors.toSet());

        // First pass: Create all mapper nodes without forwarding
        Map<String, CommandNode<CommandSourceStack>> createdNodes = new HashMap<>();

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                ResourceLocation input = mapper.inputKey();
                String nodeName = "have-" + input + "-need-" + output;

                // Only create if it doesn't exist yet
                if (!createdNodes.containsKey(nodeName)) {
                    CommandNode<CommandSourceStack> node = new ZPSLiteral.Builder<CommandSourceStack>(nodeName).build();
                    mappers.addChild(node);
                    createdNodes.put(nodeName, node);
                }
            }
        }

        // Second pass: Add mapper commands with forwarding
        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                ResourceLocation input = mapper.inputKey();
                boolean done = output.equals(mapper.outputKey());
                String nodeName = "have-" + input + "-need-" + output;

                CommandNode<CommandSourceStack> parentNode = createdNodes.get(nodeName);
                CommandNode<CommandSourceStack> destination = done ? executors : createdNodes.get("have-" + mapper.outputKey() + "-need-" + output);

                if (mapper instanceof ScriptMapper2<?,?> scriptMapper2) {
                    MappedArgumentType<?, ?> argumentType = scriptMapper2.argumentType();
                    String argumentKey = mapper.displayName() + "_argument_" + mapper.inputKey();

                    @SuppressWarnings("rawtypes")
                    ZPSArgument.Builder argumentBuilder = ZPSArgument.Builder.argument(argumentKey, argumentType.delegate());

                    @SuppressWarnings("unchecked")
                    var builtArgument = (ZPSArgument.Builder<CommandSourceStack, Object>) argumentBuilder
                            .forward(destination, context -> {
                        CommandSourceStack commandSource = (CommandSourceStack) context.getSource();
                        if (commandSource.source instanceof ZPSScriptCommandSource source) {
                            var rawArg = context.getArgument(argumentKey, argumentType.argumentClass());

                            @SuppressWarnings("unchecked")
                            var otherValue = ((java.util.function.BiFunction<Object, CommandSourceStack, Object>) argumentType.mapper())
                                    .apply(rawArg, commandSource);

                            var mapperFunction = (java.util.function.BiFunction<Object, ScriptContext, Object>) mapper.function();
                            source.value = mapperFunction.apply(source.value,
                                    new ScriptMapper2ContextImpl<>(otherValue, source.getPos(), commandSource.getLevel()));

                            if (source.execute != null && source.value.getClass().equals(source.desiredOutputType)) {
                                source.execute.accept(source.value);
                            }
                        }
                        return List.of(context.getSource());
                    }, false);

                    parentNode.addChild(
                            new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).then(builtArgument).build());
                } else {
                    parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).forward(destination, context -> {
                        if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                            var mapperFunction = (java.util.function.BiFunction<Object, ScriptContext, Object>) mapper.function();
                            source.value = mapperFunction.apply(source.value, new ScriptContextImpl(context, source.getPos(), context.getSource().getLevel()));

                            if (source.execute != null && source.value.getClass().equals(source.desiredOutputType)) {
                                source.execute.accept(source.value);
                            }

                        }
                        return List.of(context.getSource());
                    }, false).build());
                }
            }
        }
    }

    private record ScriptContextImpl(CommandContext<CommandSourceStack> context, BlockPos pos, ServerLevel level) implements ScriptContext { }

    private record ScriptMapper2ContextImpl<T>(T otherValue, BlockPos pos, ServerLevel level) implements ScriptMapper2.Context<T> { }

}
