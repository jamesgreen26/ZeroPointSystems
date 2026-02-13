package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.commands.api_impl.arguments.ZPSArgument;
import g_mungus.zps.commands.api_impl.arguments.ZPSLiteral;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.command.EnumArgument;

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
        allOutputs.addAll(Registry.EXECUTORS.stream().map(ScriptExecutor::inputKey).toList());

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper: mapperGraph.findAllMappersLeadingTo(output)) {
                for (var getter : Registry.GETTERS) {
                    if (getter.outputKey().equals(mapper.inputKey())) {

                        CommandNode<CommandSourceStack> destination = getOrCreateMapperNode("have-" + getter.outputKey() + "-need-" + output);
                        getters.addChild(new ZPSLiteral.Builder<CommandSourceStack>("need-" + output).then(new ZPSLiteral.Builder<CommandSourceStack>(getter.displayName()).forward(destination, context -> {
                            if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                                source.predicateValue = getter.function().apply(new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel()));
                            }
                            return List.of(context.getSource());
                        }, false)).build());
                    }
                }
            }
        }
    }

    public void buildMappers() {
        Set<ResourceLocation> allOutputs = Registry.MAPPERS.stream().map(ScriptMapper::outputKey).collect(Collectors.toSet());
        allOutputs.addAll(Registry.EXECUTORS.stream().map(ScriptExecutor::inputKey).toList());

        for (ResourceLocation output : allOutputs) {
            for (ScriptMapper<?, ?> mapper : mapperGraph.findAllMappersLeadingTo(output)) {
                ResourceLocation input = mapper.inputKey();
                String nodeName = "have-" + input + "-need-" + output;
                CommandNode<CommandSourceStack> parentNode = getOrCreateMapperNode(nodeName);
                CommandNode<CommandSourceStack> destination = getOrCreateMapperNode("have-" + mapper.outputKey() + "-need-" + output);

                if (mapper instanceof ScriptMapper2<?, ?, ?> scriptMapper2) {
                    addMapper2Branch(scriptMapper2, mapper, parentNode, destination);
                } else {
                    addMapper1Branch(mapper, parentNode, destination);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addMapper2Branch(ScriptMapper2<?, ?, ?> scriptMapper2, ScriptMapper<?, ?> mapper,
                                  CommandNode<CommandSourceStack> parentNode, CommandNode<CommandSourceStack> destination) {
        String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + scriptMapper2.argumentHint();
        ZPSArgument.Builder<CommandSourceStack, Object> argumentBuilder = ZPSArgument.Builder.argument(argumentKey, (ArgumentType<Object>) scriptMapper2.argumentType());

        BiFunction<Object, ScriptContext, Object> mapperFunction = (BiFunction<Object, ScriptContext, Object>) mapper.function();

        var builtArgument = argumentBuilder.forward(destination, context -> {
            try {
                CommandSourceStack commandSource = context.getSource();
                if (commandSource.source instanceof ZPSScriptCommandSource source) {
                    Object rawArg = context.getArgument(argumentKey, scriptMapper2.argumentClass());
                    source.predicateValue = mapperFunction.apply(source.predicateValue,
                            new ScriptContextWithArgumentImpl<>(rawArg, source.getPos(), commandSource.getLevel(), commandSource));
                }
                return List.of(context.getSource());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, false);

        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).then(builtArgument).build());
    }

    @SuppressWarnings("unchecked")
    private void addMapper1Branch(ScriptMapper<?, ?> mapper, CommandNode<CommandSourceStack> parentNode,
                                  CommandNode<CommandSourceStack> destination) {
        BiFunction<Object, ScriptContext, Object> mapperFunction = (BiFunction<Object, ScriptContext, Object>) mapper.function();
        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).forward(destination, context -> {
            try {
                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                    source.predicateValue = mapperFunction.apply(source.predicateValue,
                            new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel()));
                }
                return List.of(context.getSource());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, false).build());
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

    public void buildExecutors() {
        CommandNode<CommandSourceStack> booleanMapperNode = getOrCreateMapperNode("have-" + ResourceLocation.parse("zps:boolean") + "-need-" + ResourceLocation.parse("zps:boolean"));

        for (var executor : Registry.EXECUTORS) {
            for (var parentNode : List.of(booleanMapperNode, executors)) {
                addExecutorBranch(executor, parentNode);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <I, A> void addExecutorBranch(ScriptExecutor<?, ?> executor, CommandNode<CommandSourceStack> parentNode) {
        ScriptExecutor<I, A> typed = (ScriptExecutor<I, A>) executor;
        String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + typed.inputKey().getPath();

        ZPSArgument.Builder<CommandSourceStack, A> argumentBuilder = ZPSArgument.Builder.argument(argumentKey, typed.argumentType());

        var builtArgument = argumentBuilder.executes(context -> {
            CommandSourceStack commandSource = context.getSource();
            if (commandSource.source instanceof ZPSScriptCommandSource source) {
                if (!source.predicate.test(source.predicateValue)) {
                    source.sendSystemMessage(Component.literal("Predicate failed"));
                    return 0;
                }

                A rawArg = context.getArgument(argumentKey, typed.argumentClass());
                ScriptContext plainContext = new ScriptContextImpl(commandSource, source.getPos(), commandSource.getLevel());
                I mappedValue = typed.argumentMapper().apply(rawArg, plainContext);
                var argContext = new ScriptContextWithArgumentImpl<>(mappedValue, source.getPos(), commandSource.getLevel(), commandSource);
                return typed.function().apply(mappedValue, argContext);
            }
            return 0;
        });

        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(typed.displayName()).then(builtArgument).build());

        String argumentKey2 = "zps:argument_" + String.format("%06d", argumentIndex++) + ":COMPUTE";

        SuggestionProvider<CommandSourceStack> noSuggestionProvider = (context, builder) -> Suggestions.empty();

        ZPSArgument.Builder<CommandSourceStack, ComputeKey> argumentBuilder2 = ZPSArgument.Builder.argument(argumentKey2, EnumArgument.enumArgument(ComputeKey.class));

        CommandNode<CommandSourceStack> getterDestination = getters.getChild("need-" + executor.inputKey());


        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(typed.displayName()).then(
                argumentBuilder2
                        .suggests(noSuggestionProvider)
                        .forward(getterDestination, (commandContext) -> {
                            if (commandContext.getSource().source instanceof ZPSScriptCommandSource source) {
                                source.execute = executor.function();
                                source.executeType = executor.inputType();
                            }
                            return Collections.singleton(commandContext.getSource());
                        }, false)
        ).build());
    }

    private record ScriptContextImpl(CommandSourceStack commandSource, BlockPos pos, ServerLevel level) implements ScriptContext {}

    private record ScriptContextWithArgumentImpl<T>(T argumentValue, BlockPos pos, ServerLevel level, CommandSourceStack commandSource) implements ScriptContext.WithArgument<T> { }

    @SuppressWarnings("unused")
    public enum ComputeKey {COMPUTE}
}
