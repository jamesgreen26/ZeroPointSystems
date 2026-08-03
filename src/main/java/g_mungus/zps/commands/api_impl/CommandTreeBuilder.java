package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
import g_mungus.zps.commands.api_impl.arguments.ValueOfOrLiteralArgumentType;
import g_mungus.zps.commands.api_impl.arguments.ZPSArgument;
import g_mungus.zps.commands.api_impl.arguments.ArgumentPlaceholder;
import g_mungus.zps.commands.api_impl.arguments.AddressReference;
import g_mungus.zps.commands.api_impl.arguments.OverloadedExecutorArgumentType;
import g_mungus.zps.commands.api_impl.arguments.ZPSLiteral;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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

        ResourceLocation argTypeKey = scriptMapper2.argumentTypeKey();
        ArgumentType<Object> argType = argTypeKey != null
                ? ValueOfOrLiteralArgumentType.of((ArgumentType<Object>) scriptMapper2.argumentType(), argTypeKey)
                : (ArgumentType<Object>) scriptMapper2.argumentType();
        ZPSArgument.Builder<CommandSourceStack, Object> argumentBuilder = ZPSArgument.Builder.argument(argumentKey, argType);

        BiFunction<Object, ScriptContext, Object> mapperFunction = (BiFunction<Object, ScriptContext, Object>) mapper.function();

        var builtArgument = argumentBuilder.forward(destination, context -> {
            try {
                CommandSourceStack commandSource = context.getSource();
                if (commandSource.source instanceof ZPSScriptCommandSource source) {
                    Object rawArg = context.getArgument(argumentKey, Object.class);
                    if (rawArg instanceof ArgumentPlaceholder) {
                        throw new IllegalArgumentException("Argument placeholder %s must be replaced before execution");
                    }
                    if (rawArg instanceof ValueOfExpression<?> expr) {
                        rawArg = expr.evaluate(commandSource, source.getPos());
                    }
                    rawArg = resolveAddressReference(rawArg, source);
                    rawArg = coerceArgument(rawArg, scriptMapper2.argumentClass(), commandSource);
                    source.predicateValue = mapperFunction.apply(source.predicateValue,
                            new ScriptContextWithArgumentImpl<>(rawArg, source.getPos(), commandSource.getLevel(), commandSource));
                }
                return List.of(context.getSource());
            } catch (Exception e) {
                logCommandException(context, "mapper argument", mapper.displayName(), e);
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
                logCommandException(context, "mapper", mapper.displayName(), e);
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

        Map<String, List<ScriptExecutor<?, ?>>> executorsByName = new LinkedHashMap<>();
        for (var executor : Registry.EXECUTORS) {
            executorsByName.computeIfAbsent(executor.displayName(), ignored -> new ArrayList<>()).add(executor);
        }

        for (var executorGroup : executorsByName.values()) {
            if (executorGroup.size() == 1) {
                ScriptExecutor<?, ?> executor = executorGroup.get(0);
                addExecutorBranch(executor, executors, false);
                addExecutorBranch(executor, booleanMapperNode, true);
            } else {
                addOverloadedExecutorBranch(executorGroup, executors, false);
                addOverloadedExecutorBranch(executorGroup, booleanMapperNode, true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <I, A> void addExecutorBranch(ScriptExecutor<?, ?> executor, CommandNode<CommandSourceStack> parentNode, boolean isConditional) {
        ScriptExecutor<I, A> typed = (ScriptExecutor<I, A>) executor;
        String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + typed.inputKey().getPath();

        ArgumentType<Object> argumentType = ValueOfOrLiteralArgumentType.of((ArgumentType<Object>) typed.argumentType(), typed.inputKey());
        ZPSArgument.Builder<CommandSourceStack, Object> argumentBuilder = ZPSArgument.Builder.argument(argumentKey, argumentType);

        var builtArgument = argumentBuilder.executes(context -> {
            CommandSourceStack commandSource = context.getSource();
            if (commandSource.source instanceof ZPSScriptCommandSource source) {
                if (source.execute != null) {
                    return source.execute.get();
                }
                if (source.predicate.test(source.predicateValue)) {
                    Object rawArg = context.getArgument(argumentKey, Object.class);
                    return executeExecutor(typed, rawArg, commandSource, source);

                }
            }
            return 0;
        });

        if (isConditional) {
            builtArgument.then((new ZPSLiteral.Builder<CommandSourceStack>("else")).forward(executors, context -> {
                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                    boolean matched = source.predicate.test(source.predicateValue);
                    source.predicate = source.predicate.cycle();

                    if (matched && source.execute == null) {
                        source.execute = () -> {
                            Object rawArg = context.getArgument(argumentKey, Object.class);
                            return executeExecutor(typed, rawArg, context.getSource(), source);
                        };
                    }
                }
                return List.of(context.getSource());
            }, false));
        }

        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(typed.displayName()).then(builtArgument).build());
    }

    private void addOverloadedExecutorBranch(List<ScriptExecutor<?, ?>> executorGroup, CommandNode<CommandSourceStack> parentNode, boolean isConditional) {
        String displayName = executorGroup.get(0).displayName();
        String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + displayName + "_overload";

        ZPSArgument.Builder<CommandSourceStack, OverloadedExecutorArgumentType.ParsedOverloads> argumentBuilder =
                ZPSArgument.Builder.argument(argumentKey, new OverloadedExecutorArgumentType(executorGroup));

        var builtArgument = argumentBuilder.executes(context -> {
            CommandSourceStack commandSource = context.getSource();
            if (commandSource.source instanceof ZPSScriptCommandSource source) {
                if (source.execute != null) {
                    return source.execute.get();
                }
                if (source.predicate.test(source.predicateValue)) {
                    OverloadedExecutorArgumentType.ParsedOverloads parsed =
                            context.getArgument(argumentKey, OverloadedExecutorArgumentType.ParsedOverloads.class);
                    return executeOverloadedExecutor(parsed, commandSource, source);
                }
            }
            return 0;
        });

        if (isConditional) {
            builtArgument.then((new ZPSLiteral.Builder<CommandSourceStack>("else")).forward(executors, context -> {
                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                    boolean matched = source.predicate.test(source.predicateValue);
                    source.predicate = source.predicate.cycle();

                    if (matched && source.execute == null) {
                        source.execute = () -> {
                            OverloadedExecutorArgumentType.ParsedOverloads parsed =
                                    context.getArgument(argumentKey, OverloadedExecutorArgumentType.ParsedOverloads.class);
                            return executeOverloadedExecutor(parsed, context.getSource(), source);
                        };
                    }
                }
                return List.of(context.getSource());
            }, false));
        }

        parentNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(displayName).then(builtArgument).build());
    }

    @SuppressWarnings("unchecked")
    private static <I, A> int executeExecutor(ScriptExecutor<?, ?> executor, Object rawArg, CommandSourceStack commandSource, ZPSScriptCommandSource source) {
        ScriptExecutor<I, A> typed = (ScriptExecutor<I, A>) executor;
        if (rawArg instanceof ArgumentPlaceholder) {
            throw new IllegalArgumentException("Argument placeholder %s must be replaced before execution");
        }
        if (rawArg instanceof ValueOfExpression<?> expr) {
            rawArg = expr.evaluate(commandSource, source.getPos());
        }
        rawArg = resolveAddressReference(rawArg, source);
        rawArg = coerceArgument(rawArg, typed.argumentClass(), commandSource);
        ScriptContext executorContext = new ScriptContextImpl(commandSource, source.getPos(), commandSource.getLevel());
        I mappedValue = typed.argumentMapper().apply((A) rawArg, executorContext);
        return typed.function().apply(mappedValue, executorContext);
    }

    private static int executeOverloadedExecutor(OverloadedExecutorArgumentType.ParsedOverloads parsed, CommandSourceStack commandSource, ZPSScriptCommandSource source) {
        ResourceLocation targetBlock = targetBlockKey(commandSource, source.getPos());

        for (OverloadedExecutorArgumentType.ParsedExecutorArgument parsedArgument : parsed.arguments()) {
            ScriptExecutor<?, ?> executor = parsedArgument.executor();
            if (executor != null && appliesToTargetBlock(executor, targetBlock, true)) {
                return executeExecutor(executor, parsedArgument.rawArgument(), commandSource, source);
            }
        }

        for (OverloadedExecutorArgumentType.ParsedExecutorArgument parsedArgument : parsed.arguments()) {
            ScriptExecutor<?, ?> executor = parsedArgument.executor();
            if (executor != null && appliesToTargetBlock(executor, targetBlock, false)) {
                return executeExecutor(executor, parsedArgument.rawArgument(), commandSource, source);
            }
        }

        return 0;
    }

    private static boolean appliesToTargetBlock(ScriptExecutor<?, ?> executor, @Nullable ResourceLocation targetBlock, boolean requireExplicitMatch) {
        Set<ResourceLocation> associatedBlocks = executor.associatedBlocks();
        if (associatedBlocks == null) {
            return !requireExplicitMatch;
        }
        return targetBlock != null && associatedBlocks.contains(targetBlock);
    }

    private static @Nullable ResourceLocation targetBlockKey(CommandSourceStack commandSource, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(commandSource.getLevel().getBlockState(pos).getBlock());
    }

    public void buildValueOfDispatchers() {
        for (ResourceLocation targetType : TypeKeys.TYPE_KEY_TO_CLASS.keySet()) {
            CommandDispatcher<CommandSourceStack> inner = new CommandDispatcher<>();
            Map<String, CommandNode<CommandSourceStack>> innerNodes = new HashMap<>();

            Command<CommandSourceStack> terminal = context -> {
                if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                    source.pendingResult = source.predicateValue;
                }
                return 1;
            };

            // Pre-create target node so all branches converge on the same instance.
            String terminalName = "have-" + targetType;
            CommandNode<CommandSourceStack> terminalNode = new ZPSLiteral.Builder<CommandSourceStack>(terminalName)
                    .then(new ZPSLiteral.Builder<CommandSourceStack>(ValueOfDispatchers.TERMINAL_LITERAL)
                            .executes(terminal))
                    .build();
            innerNodes.put(terminalName, terminalNode);

            Set<ScriptMapper<?, ?>> allMappers = mapperGraph.findAllMappersLeadingTo(targetType);

            // Build inner mapper nodes
            for (ScriptMapper<?, ?> mapper : allMappers) {
                String inputName = "have-" + mapper.inputKey();
                String outputName = "have-" + mapper.outputKey();
                CommandNode<CommandSourceStack> inputNode = getOrCreateInnerNode(innerNodes, inputName, targetType, terminal);
                CommandNode<CommandSourceStack> outputNode = getOrCreateInnerNode(innerNodes, outputName, targetType, terminal);

                if (mapper instanceof ScriptMapper2<?, ?, ?> scriptMapper2) {
                    addInnerMapper2Branch(scriptMapper2, mapper, inputNode, outputNode);
                } else {
                    addInnerMapper1Branch(mapper, inputNode, outputNode);
                }
            }

            // Add getter nodes at the inner dispatcher root
            for (ScriptGetter<?> getter : Registry.GETTERS) {
                boolean canReach = getter.outputKey().equals(targetType) ||
                        allMappers.stream().anyMatch(m -> m.inputKey().equals(getter.outputKey()));
                if (!canReach) continue;

                String outputName = "have-" + getter.outputKey();
                CommandNode<CommandSourceStack> outputNode = getOrCreateInnerNode(innerNodes, outputName, targetType, terminal);

                ZPSLiteral.Builder<CommandSourceStack> getterBuilder =
                        new ZPSLiteral.Builder<>(getter.displayName());
                getterBuilder.forward(outputNode, context -> {
                    if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                        source.predicateValue = getter.function().apply(
                                new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel())
                        );
                    }
                    return List.of(context.getSource());
                }, false);
                inner.getRoot().addChild(getterBuilder.build());
            }

            ValueOfDispatchers.register(targetType, inner);
        }
    }

    private CommandNode<CommandSourceStack> getOrCreateInnerNode(
            Map<String, CommandNode<CommandSourceStack>> innerNodes,
            String name,
            ResourceLocation targetType,
            Command<CommandSourceStack> terminal
    ) {
        return innerNodes.computeIfAbsent(name, n -> {
            ZPSLiteral.Builder<CommandSourceStack> builder = new ZPSLiteral.Builder<>(n);
            if (("have-" + targetType).equals(n)) {
                builder.then(new ZPSLiteral.Builder<CommandSourceStack>(ValueOfDispatchers.TERMINAL_LITERAL)
                        .executes(terminal));
            }
            return builder.build();
        });
    }

    @SuppressWarnings("unchecked")
    private void addInnerMapper2Branch(ScriptMapper2<?, ?, ?> scriptMapper2, ScriptMapper<?, ?> mapper,
                                       CommandNode<CommandSourceStack> inputNode, CommandNode<CommandSourceStack> outputNode) {
        String argumentKey = "zps:argument_" + String.format("%06d", argumentIndex++) + ":" + scriptMapper2.argumentHint();

        ResourceLocation argTypeKey = scriptMapper2.argumentTypeKey();
        ArgumentType<Object> argType = argTypeKey != null
                ? ValueOfOrLiteralArgumentType.of((ArgumentType<Object>) scriptMapper2.argumentType(), argTypeKey)
                : (ArgumentType<Object>) scriptMapper2.argumentType();
        ZPSArgument.Builder<CommandSourceStack, Object> argumentBuilder = ZPSArgument.Builder.argument(argumentKey, argType);

        BiFunction<Object, ScriptContext, Object> mapperFunction = (BiFunction<Object, ScriptContext, Object>) mapper.function();

        var builtArgument = argumentBuilder.forward(outputNode, context -> {
            try {
                CommandSourceStack commandSource = context.getSource();
                if (commandSource.source instanceof ZPSScriptCommandSource source) {
                    Object rawArg = context.getArgument(argumentKey, Object.class);
                    if (rawArg instanceof ArgumentPlaceholder) {
                        throw new IllegalArgumentException("Argument placeholder %s must be replaced before execution");
                    }
                    if (rawArg instanceof ValueOfExpression<?> expr) {
                        rawArg = expr.evaluate(commandSource, source.getPos());
                    }
                    rawArg = resolveAddressReference(rawArg, source);
                    rawArg = coerceArgument(rawArg, scriptMapper2.argumentClass(), commandSource);
                    source.predicateValue = mapperFunction.apply(source.predicateValue,
                            new ScriptContextWithArgumentImpl<>(rawArg, source.getPos(), commandSource.getLevel(), commandSource));
                }
                return List.of(context.getSource());
            } catch (Exception e) {
                logCommandException(context, "value_of mapper argument", mapper.displayName(), e);
                throw new RuntimeException(e);
            }
        }, false);
        inputNode.addChild(new ZPSLiteral.Builder<CommandSourceStack>(mapper.displayName()).then(builtArgument).build());
    }

    @SuppressWarnings("unchecked")
    private void addInnerMapper1Branch(ScriptMapper<?, ?> mapper,
                                       CommandNode<CommandSourceStack> inputNode, CommandNode<CommandSourceStack> outputNode) {
        BiFunction<Object, ScriptContext, Object> mapperFunction = (BiFunction<Object, ScriptContext, Object>) mapper.function();
        ZPSLiteral.Builder<CommandSourceStack> mapperBuilder =
                new ZPSLiteral.Builder<>(mapper.displayName());
        mapperBuilder.forward(outputNode, context -> {
                    try {
                        if (context.getSource().source instanceof ZPSScriptCommandSource source) {
                            source.predicateValue = mapperFunction.apply(source.predicateValue,
                                    new ScriptContextImpl(context.getSource(), source.getPos(), context.getSource().getLevel()));
                        }
                        return List.of(context.getSource());
                    } catch (Exception e) {
                        logCommandException(context, "value_of mapper", mapper.displayName(), e);
                        throw new RuntimeException(e);
                    }
                }, false);
        inputNode.addChild(mapperBuilder.build());
    }

    private record ScriptContextImpl(CommandSourceStack commandSource, BlockPos pos, ServerLevel level) implements ScriptContext {}

    private record ScriptContextWithArgumentImpl<T>(T argumentValue, BlockPos pos, ServerLevel level, CommandSourceStack commandSource) implements ScriptContext.WithArgument<T> { }

    private static void logCommandException(CommandContext<CommandSourceStack> context, String phase, String commandPart, Exception exception) {
        String input = context.getInput();
        String fullCommand = input;
        if (context.getSource().source instanceof ZPSScriptCommandSource source && source.getCommandInput() != null) {
            fullCommand = source.getCommandInput();
        }
        String location = formatCommandLocation(input, getLastNodeRange(context));
        if (fullCommand.equals(input)) {
            ZPSMod.LOGGER.error("Script command failed while evaluating {} '{}'\nCommand: {}\nLocation:\n{}",
                    phase, commandPart, fullCommand, location, exception);
        } else {
            ZPSMod.LOGGER.error("Script command failed while evaluating {} '{}'\nCommand: {}\nEvaluating: {}\nLocation:\n{}",
                    phase, commandPart, fullCommand, input, location, exception);
        }
    }

    private static StringRange getLastNodeRange(CommandContext<CommandSourceStack> context) {
        List<ParsedCommandNode<CommandSourceStack>> nodes = context.getNodes();
        if (!nodes.isEmpty()) {
            return nodes.get(nodes.size() - 1).getRange();
        }
        return context.getRange();
    }

    private static String formatCommandLocation(String input, StringRange range) {
        if (input == null || input.isEmpty()) {
            return "<empty command>";
        }
        int start = Math.max(0, Math.min(input.length(), range.getStart()));
        int end = Math.max(start + 1, Math.min(input.length(), range.getEnd()));
        return input + "\n" + " ".repeat(start) + "^".repeat(Math.max(1, end - start));
    }

    private static Object resolveAddressReference(Object rawArg, ZPSScriptCommandSource source) {
        if (rawArg instanceof AddressReference addressReference) {
            BlockPos resolved = source.resolveAddress(addressReference.name());
            if (resolved == null) {
                throw new IllegalArgumentException("Address @" + addressReference.name() + " is not available in this context");
            }
            return resolved;
        }
        return rawArg;
    }

    private static Object coerceArgument(Object rawArg, Class<?> expectedClass, CommandSourceStack commandSource) {
        if (rawArg == null || expectedClass.isInstance(rawArg) || expectedClass == Object.class) {
            return rawArg;
        }

        if (rawArg instanceof Coordinates coordinates) {
            if (expectedClass == BlockPos.class) {
                return coordinates.getBlockPos(commandSource);
            }
            if (expectedClass == Vec3.class) {
                return coordinates.getPosition(commandSource);
            }
        }
        if (expectedClass == Coordinates.class) {
            if (rawArg instanceof BlockPos blockPos) {
                return new FixedCoordinates(Vec3.atCenterOf(blockPos));
            }
            if (rawArg instanceof Vec3 vec3) {
                return new FixedCoordinates(vec3);
            }
        }

        throw new IllegalArgumentException("Expected argument type " + expectedClass.getSimpleName()
                + ", got " + rawArg.getClass().getSimpleName());
    }

    private record FixedCoordinates(Vec3 position) implements Coordinates {
        @Override
        public @NotNull Vec3 getPosition(CommandSourceStack source) {
            return position;
        }

        @Override
        public @NotNull Vec2 getRotation(CommandSourceStack source) {
            return source.getRotation();
        }

        @Override
        public boolean isXRelative() {
            return false;
        }

        @Override
        public boolean isYRelative() {
            return false;
        }

        @Override
        public boolean isZRelative() {
            return false;
        }
    }

    @SuppressWarnings("unused")
    public enum ComputeKey {compute}
}
