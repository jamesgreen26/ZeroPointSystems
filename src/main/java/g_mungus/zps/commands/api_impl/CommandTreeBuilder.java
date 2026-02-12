package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.commands.api_impl.arguments.ZPSArgument;
import g_mungus.zps.commands.api_impl.arguments.ZPSLiteral;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class CommandTreeBuilder {

    public final CommandDispatcher<CommandSourceStack> dispatcher;

    CommandNode<CommandSourceStack> executors;
    CommandNode<CommandSourceStack> mappers;
    CommandNode<CommandSourceStack> getters;

    private final MapperGraph mapperGraph;
    private final Map<String, CommandNode<CommandSourceStack>> mapperNodes;
    private int argumentIndex = 0;

    public CommandTreeBuilder(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;
        this.mapperNodes = new HashMap<>();

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

    private CommandNode<CommandSourceStack> getOrCreateMapperNode(String nodeName) {
        return mapperNodes.computeIfAbsent(nodeName, name -> {
            CommandNode<CommandSourceStack> node = new ZPSLiteral.Builder<CommandSourceStack>(name).build();
            mappers.addChild(node);
            return node;
        });
    }

    public void buildGetters() {

        Set<ResourceLocation> allOutputs = Registry.MAPPERS.stream().map(ScriptMapper::outputKey).collect(Collectors.toSet());

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                for (var getter : Registry.GETTERS) {
                    if (getter.outputKey().equals(mapper.inputKey())) {

                        CommandNode<CommandSourceStack> destination = getOrCreateMapperNode("have-" + getter.outputKey() + "-need-" + output);
                        getters.addChild(new ZPSLiteral.Builder<CommandSourceStack>("need-" + output).then(new ZPSLiteral.Builder<CommandSourceStack>(getter.displayName()).forward(destination, context -> {
                            if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                source.predicateValue = getter.function().apply(new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel()));

                                if (source.execute != null && source.predicateValue.getClass().equals(source.desiredOutputType)) {
                                    source.execute.accept(source.predicateValue);
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

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                ResourceLocation input = mapper.inputKey();
                String nodeName = "have-" + input + "-need-" + output;

                CommandNode<CommandSourceStack> parentNode = getOrCreateMapperNode(nodeName);

                // Mappers never redirect to executors - they always redirect to the next "have-X-need-Y" node
                CommandNode<CommandSourceStack> destination = getOrCreateMapperNode("have-" + mapper.outputKey() + "-need-" + output);

                if (mapper instanceof ScriptMapper2<?,?,?> scriptMapper2) {
                    // Include output to make argument keys unique per destination
                    String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + scriptMapper2.argumentHint();

                    @SuppressWarnings("rawtypes")
                    ZPSArgument.Builder argumentBuilder = ZPSArgument.Builder.argument(argumentKey, scriptMapper2.argumentType());

                    @SuppressWarnings("unchecked")
                    var builtArgument = (ZPSArgument.Builder<CommandSourceStack, Object>) argumentBuilder
                            .forward(destination, context -> {
                                try {
                                    CommandSourceStack commandSource = (CommandSourceStack) context.getSource();
                                    if (commandSource.source instanceof ZPSScriptCommandSource source) {
                                        var rawArg = context.getArgument(argumentKey, scriptMapper2.argumentClass());

                                        var mapperFunction = (java.util.function.BiFunction<Object, ScriptContext, Object>) mapper.function();
                                        source.predicateValue = mapperFunction.apply(source.predicateValue,
                                                new ScriptContextWithArgumentImpl<>(rawArg, source.getPos(), commandSource.getLevel(), commandSource));

                                        if (source.execute != null && source.predicateValue.getClass().equals(source.desiredOutputType)) {
                                            source.execute.accept(source.predicateValue);
                                        }
                                    }
                                    return List.of(context.getSource());
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                    }, false);

                    parentNode.addChild(
                            new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).then(builtArgument).build());
                } else {
                    parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).forward(destination, context -> {
                        try {
                            if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                var mapperFunction = (java.util.function.BiFunction<Object, ScriptContext, Object>) mapper.function();
                                source.predicateValue = mapperFunction.apply(source.predicateValue, new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel()));

                                if (source.execute != null && source.predicateValue.getClass().equals(source.desiredOutputType)) {
                                    source.execute.accept(source.predicateValue);
                                }

                            }
                            return List.of(context.getSource());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, false).build());
                }
            }
        }
    }

    public void buildConditionalExecutors() {
        // Get the target node: getters -> "need-zps:boolean"
        CommandNode<CommandSourceStack> booleanGetterNode = getters.getChild("need-" + ResourceLocation.parse("zps:boolean"));

        if (booleanGetterNode != null) {
            // Add "if" literal that redirects to boolean getter and sets predicate type
            executors.addChild(
                    new ZPSLiteral.Builder<CommandSourceStack>("if")
                            .forward(booleanGetterNode, context -> {
                                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                    source.predicate = ZPSScriptCommandSource.PredicateType.IF;
                                }
                                return List.of(context.getSource());
                            }, false)
                            .build()
            );

            // Add "unless" literal that redirects to boolean getter and sets predicate type
            executors.addChild(
                    new ZPSLiteral.Builder<CommandSourceStack>("unless")
                            .forward(booleanGetterNode, context -> {
                                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                    source.predicate = ZPSScriptCommandSource.PredicateType.UNLESS;
                                }
                                return List.of(context.getSource());
                            }, false)
                            .build()
            );
        }
    }

    @SuppressWarnings("unchecked")
    public void buildExecutors() {
        CommandNode<CommandSourceStack> booleanMapperNode = getOrCreateMapperNode("have-" + ResourceLocation.parse("zps:boolean") + "-need-" + ResourceLocation.parse("zps:boolean"));

        for (var executor : Registry.EXECUTORS) {
            for (var parentNode : List.of(booleanMapperNode, executors)) {

                String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + executor.inputKey().getPath();

                @SuppressWarnings("rawtypes")
                ZPSArgument.Builder argumentBuilder = ZPSArgument.Builder.argument(argumentKey, executor.argumentType());

                @SuppressWarnings("unchecked")
                var builtArgument = (ZPSArgument.Builder<CommandSourceStack, Object>) argumentBuilder
                        .executes(context -> {
                    CommandSourceStack commandSource = (CommandSourceStack) context.getSource();
                    if (commandSource.source instanceof ZPSScriptCommandSource source) {
                        // Test the predicate (IF/UNLESS/NONE)
                        if (!source.predicate.test(source.predicateValue)) {
                            source.sendSystemMessage(Component.literal("Predicate failed"));
                            return 0; // Predicate failed, don't execute
                        }

                        // Extract the raw argument
                        Object rawArg = context.getArgument(argumentKey, executor.argumentClass());

                        // Create context for argumentMapper
                        ScriptContext plainContext = new ScriptContextImpl(
                                commandSource, source.getPos(), commandSource.getLevel()
                        );

                        // Apply argumentMapper to convert A -> I
                        @SuppressWarnings("rawtypes")
                        BiFunction argumentMapper = executor.argumentMapper();
                        @SuppressWarnings("unchecked")
                        Object mappedValue = argumentMapper.apply(rawArg, plainContext);

                        // Create context with the mapped value
                        var argContext = new ScriptContextWithArgumentImpl<>(
                                mappedValue, source.getPos(), commandSource.getLevel(), commandSource
                        );

                        // Execute the executor function
                        @SuppressWarnings("rawtypes")
                        BiFunction executorFunction = executor.function();
                        @SuppressWarnings("unchecked")
                        Integer result = (Integer) executorFunction.apply(mappedValue, argContext);
                        return result;
                    }
                    return 0; // Not a ZPSScriptCommandSource
                });

                parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(executor.displayName()).then(builtArgument).build());
            }
        }
    }

    private record ScriptContextImpl(CommandSourceStack commandSource, BlockPos pos, ServerLevel level) implements ScriptContext {}

    private record ScriptContextWithArgumentImpl<T>(T argumentValue, BlockPos pos, ServerLevel level, CommandSourceStack commandSource) implements ScriptContext.WithArgument<T> { }

}
