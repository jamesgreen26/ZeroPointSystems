package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptNode;
import g_mungus.zps.commands.api_impl.arguments.ZPSLiteral;
import g_mungus.zps.commands.api_impl.exceptions.UnsupportedOperationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
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

        registerPredicates();
        this.mapperGraph = new MapperGraph();
        Registry.MAPPERS.forEach(mapperGraph::addMapper);
    }


    public void registerPredicates() {
        Map<ResourceLocation, Class<?>> allOutputs = new HashMap<>();
        for (var existingMapper: Registry.MAPPERS) {
            allOutputs.put(existingMapper.outputKey(), existingMapper.outputType());
        }

        for (var existingGetter: Registry.GETTERS) {
            allOutputs.put(existingGetter.outputKey(), existingGetter.outputType());
        }

        for (var output: allOutputs.entrySet()) {
            Registry.register(new ScriptMapper<>(
                    "equals",
                    output.getValue(),
                    Boolean.class,
                    output.getKey(),
                    ResourceLocation.parse("zps:boolean"),
                    (in, context) -> {
                        if (context instanceof ScriptContextImpl ctx) {
                            return in.equals(ctx.context.getArgument("value", output.getValue()));
                        } else {
                            throw new IllegalStateException();
                        }
                    }
            ));
            if (Number.class.isAssignableFrom(output.getValue())) {
                Registry.register(new ScriptMapper<>(
                        ">",
                        output.getValue(),
                        Boolean.class,
                        output.getKey(),
                        ResourceLocation.parse("zps:boolean"),
                        (in, context) -> {
                            if (context instanceof ScriptContextImpl ctx && in instanceof Number num) {
                                Number other = (Number) ctx.context.getArgument("value", output.getValue());
                                return num.doubleValue() > other.doubleValue();
                            } else {
                                throw new IllegalStateException();
                            }
                        }
                ));
            }
        }
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

    private static UnsupportedOperationException accessViolation() {
        return new UnsupportedOperationException(Component.literal("Cannot invoke internal commands directly"));
    }

    private record ScriptContextImpl(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, BlockPos pos, ServerLevel level) implements ScriptContext { }
}
