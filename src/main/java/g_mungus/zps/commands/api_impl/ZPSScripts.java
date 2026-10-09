package g_mungus.zps.commands.api_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.MungusScript;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.engine.codec.ScriptTreeCodec;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.sync.ZPSHostCodec;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One build of the script language on the server: the engine made from every registered node, in
 * a dispatcher of its own so none of it shows in chat. Made again whenever the server's commands
 * are, which is on start and on {@code /reload}.
 */
public final class ZPSScripts {
    private static final String GRAFT = "script";
    private static final String RUN = "run";

    private static volatile @Nullable ZPSScripts current;

    private final ZPSScriptHost host = new ZPSScriptHost();
    private final ScriptEngine<CommandSourceStack> engine;
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final CommandNode<CommandSourceStack> grafted;
    private final ZPSHostCodec hostCodec;
    private final CommandBuildContext buildContext;
    private volatile byte @Nullable [] encodedTree;

    private ZPSScripts(CommandBuildContext buildContext) {
        this.buildContext = buildContext;
        ZPSScriptTypes.Registered registered = ZPSScriptTypes.collect();
        this.engine = MungusScript.engine(host, new BuildEnvironment(buildContext), registrar -> {
            registered.types().forEach(registrar::registerType);
            NeoForge.EVENT_BUS.post(new RegisterScriptCommandsEvent(registrar::register, buildContext));
        });
        this.hostCodec = new ZPSHostCodec(buildContext, registered.applicabilities());
        this.grafted = LiteralArgumentBuilder.<CommandSourceStack>literal(GRAFT).build();
        dispatcher.getRoot().addChild(grafted);
        engine.graft(grafted);
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal(RUN)
                .forward(engine.scriptRoot(), context -> List.of(engine.begin(context.getSource())), false));
    }

    /** Builds the scripts the server runs from now on. */
    static ZPSScripts build(CommandBuildContext buildContext) {
        ZPSScripts scripts = new ZPSScripts(buildContext);
        current = scripts;

        scripts.exportLanguage();

        return scripts;
    }

    /**
     * Builds the scripts again from the same registries. Some compat finds its commands in the
     * level, which does not exist yet when the server first registers its commands.
     */
    static void rebuild() {
        ZPSScripts scripts = current;
        if (scripts != null) {
            build(scripts.buildContext);
        }
    }

    /** The scripts the server is running. Only valid once the server has registered its commands. */
    public static ZPSScripts get() {
        ZPSScripts scripts = current;
        if (scripts == null) {
            throw new IllegalStateException("Scripts are only built once the server registers its commands");
        }
        return scripts;
    }

    public static @Nullable ZPSScripts getIfBuilt() {
        return current;
    }

    public ScriptEngine<CommandSourceStack> engine() {
        return engine;
    }

    /** The node the engine is grafted under. */
    public CommandNode<CommandSourceStack> tree() {
        return grafted;
    }

    /** {@code base} aimed at {@code target}, as a script command is run with. */
    public static CommandSourceStack aimedAt(CommandSourceStack base, BlockPos target) {
        return base.withSource(new ZPSScriptCommandSource(base.source, target, null));
    }

    /**
     * Runs one command, already pre-processed, against {@code target}.
     *
     * @throws CommandSyntaxException if it does not parse, or whatever a node throws while it runs
     */
    public int run(String command, CommandSourceStack base, BlockPos target) throws CommandSyntaxException {
        return dispatcher.execute(RUN + " " + command, aimedAt(base, target));
    }

    /**
     * Runs one command and says why it failed, or returns null if it ran. A failure is logged when
     * the config asks for that.
     */
    public @Nullable ScriptCommandFailure tryRun(String command, CommandSourceStack base, BlockPos target) {
        try {
            run(command, base, target);
            return null;
        } catch (Exception e) {
            ScriptCommandFailure failure = describe(e, command, command, SourceMap.IDENTITY);
            if (ZPSConfig.getScriptCommandFailureBehavior() == ZPSConfig.ScriptCommandFailureBehavior.LOG) {
                ZPSMod.LOGGER.error("Script command failed\nCommand: {}\nReason: {}", command, failure.reason());
            }
            return failure;
        }
    }

    /** What {@code value_of(expression)} gives where a {@code type} is wanted, read at {@code target}. */
    public <T> T evaluate(String expression, ScriptType<T> type, CommandSourceStack base, BlockPos target) {
        return engine.evaluate(expression, type, engine.begin(aimedAt(base, target)));
    }

    public ScriptCommandFailure describe(Throwable failure, String playerCommand, String executedCommand,
                                         SourceMap sourceMap) {
        return ScriptCommandFailure.of(engine.describe(failure, playerCommand, executedCommand, sourceMap));
    }

    /**
     * Prepares a script's lines: the engine's aliases first, then {@code more}, in order. A
     * malformed alias definition is skipped rather than stopping the script.
     */
    public CommandPreProcessor.Prepared prepare(List<String> lines, CommandSourceStack base, BlockPos target,
                                                List<CommandPreProcessor> more) {
        List<CommandPreProcessor> chain = new ArrayList<>();
        chain.add(engine.aliases());
        chain.addAll(more);
        return CommandPreProcessor.chain(chain).prepare(lines, context(base, target));
    }

    /** One command of a script {@link #prepare}d for {@code target}. */
    public PreProcessed process(CommandPreProcessor.Prepared prepared, String command, CommandSourceStack base,
                                BlockPos target) {
        return prepared.process(command, context(base, target));
    }

    private PreProcessContext context(CommandSourceStack base, BlockPos target) {
        CommandSourceStack source = aimedAt(base, target);
        return new PreProcessContext(engine.probe(source), host.hostContext(source));
    }

    /** The blocks the getter called {@code name} is for, or null if it is for every block or there is none. */
    public @Nullable BlockApplicability getterApplicability(String name) {
        for (ScriptNode node : engine.nodes()) {
            if (node instanceof ScriptGetter<?> getter && getter.displayName().equals(name)) {
                return getter.applicability() instanceof BlockApplicability blocks ? blocks : null;
            }
        }
        return null;
    }

    /** Whether a getter called {@code name} is registered. */
    public boolean hasGetter(String name) {
        return engine.nodes().stream().anyMatch(node -> node instanceof ScriptGetter<?> && node.displayName().equals(name));
    }

    /** Writes the language to {@code .mungus/zps_command_tree.mungustree} in the run folder, for editor tooling. */
    private void exportLanguage() {
        Path file = FMLPaths.GAMEDIR.get().resolve(".mungus").resolve("zps_command_tree.mungustree");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                new ScriptLanguageFile(ZPSArgumentShapes::of).write(engine, grafted, ZPSMod.MOD_ID, out);
            }
        } catch (Exception e) {
            ZPSMod.LOGGER.warn("Script language export failed", e);
        }
    }

    /** The tree as clients receive it, encoded once per build. */
    public byte[] encodedTree() {
        byte[] encoded = encodedTree;
        if (encoded == null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                new ScriptTreeCodec(hostCodec).encode(engine, grafted, out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            encoded = bytes.toByteArray();
            encodedTree = encoded;
            ZPSMod.LOGGER.debug("Encoded the script tree for clients: {} bytes", encoded.length);
        }
        return encoded;
    }
}
