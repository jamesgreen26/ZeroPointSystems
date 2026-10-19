package g_mungus.zps.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.codec.ScriptTreeCodec;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.commands.sync.ZPSHostCodec;
import g_mungus.zps.compat.Compat;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.joml.Quaterniond;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The script tree as a client receives it: encoded by the server, decoded the way
 * {@link g_mungus.zps.client.script.ClientScripts} decodes it, and read the way the editors read
 * it. Run on the server, since that is where game tests run, with a host that stands in for the
 * client's.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScriptTreeSyncGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    /** Offers a restricted node where it applies to one of {@code connected}, as the client does. */
    private record Host(ServerLevel level, Set<ScriptTarget> connected) implements ScriptViewHost<CommandSourceStack> {
        @Override
        public Object hostContext(CommandSourceStack source) {
            return this;
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            return applicability instanceof TargetApplicability target && !target.appliesToAnyTarget(level, connected)
                    ? Match.NONE
                    : Match.EXPLICIT;
        }

        @Override
        public String defaultNamespace() {
            return ZPSMod.MOD_ID;
        }
    }

    private static ScriptView<CommandSourceStack> decode(GameTestHelper helper, Set<ScriptTarget> connected) {
        ServerLevel level = helper.getLevel();
        CommandBuildContext buildContext = CommandBuildContext.simple(level.registryAccess(), level.enabledFeatures());
        try {
            ZPSScriptTypes.Registered registered = ZPSScriptTypes.collect();
            return new ScriptTreeCodec(new ZPSHostCodec(buildContext, registered.applicabilities())).decode(
                    new DataInputStream(new ByteArrayInputStream(ZPSScripts.get().encodedTree())),
                    new Host(level, connected), registered.types());
        } catch (IOException e) {
            throw new IllegalStateException("The script tree did not decode", e);
        }
    }

    /** A block of the kind {@code block} somewhere it is not. Only its kind is asked about. */
    private static ScriptTarget anywhere(ResourceLocation block) {
        return new ScriptTarget(BlockPos.ZERO, block);
    }

    private static List<String> suggest(GameTestHelper helper, ScriptView<CommandSourceStack> view, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        return view.suggest(command, command.length(), source, null).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    /** What {@code on_phys_tick command(...)} holds is a command of its own on a client: suggested and highlighted as one. */
    @GameTest(template = TEMPLATE)
    public static void onPhysTickTakesACommandOnTheClient(GameTestHelper helper) {
        BlockPos thruster = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.THRUSTER.get());
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        ScriptView<CommandSourceStack> atThruster = decode(helper, Set.of(ScriptTarget.at(level, thruster)));
        ScriptView<CommandSourceStack> atStone = decode(helper, Set.of(ScriptTarget.at(level, stone)));

        helper.assertTrue(suggest(helper, atThruster, "on_phys").contains("on_phys_tick"),
                "on_phys_tick should be offered for a thruster, got " + suggest(helper, atThruster, "on_phys"));
        helper.assertTrue(!suggest(helper, atStone, "on_phys").contains("on_phys_tick"),
                "on_phys_tick should not be offered for stone, got " + suggest(helper, atStone, "on_phys"));
        helper.assertTrue(!suggest(helper, atThruster, "on_phys_tick command(on_phys").contains("on_phys_tick"),
                "on_phys_tick should not be offered inside itself, got "
                        + suggest(helper, atThruster, "on_phys_tick command(on_phys"));
        helper.assertTrue(suggest(helper, atThruster, "on_phys_tick ").contains("command("),
                "command( should be offered after on_phys_tick, got " + suggest(helper, atThruster, "on_phys_tick "));
        helper.assertTrue(!suggest(helper, atThruster, "thru").contains("thrust"),
                "thrust should only be offered inside on_phys_tick, got " + suggest(helper, atThruster, "thru"));
        helper.assertTrue(suggest(helper, atThruster, "on_phys_tick command(thru").contains("thrust"),
                "thrust should be offered inside command(, got "
                        + suggest(helper, atThruster, "on_phys_tick command(thru"));
        helper.assertTrue(suggest(helper, atThruster, "on_phys_tick command(i").contains("if"),
                "if should be offered inside command(, got " + suggest(helper, atThruster, "on_phys_tick command(i"));

        // The editor's hint inside an open command(: what the inner executor takes, not the outer error.
        String open = "on_phys_tick command(thrust ";
        CommandSourceStack hintSource = level.getServer().createCommandSourceStack().withLevel(level);
        var inner = atThruster.parseAt(open, open.length(), hintSource);
        var at = inner.getContext().findSuggestionContext(open.length());
        Collection<String> usage = new CommandDispatcher<CommandSourceStack>().getSmartUsage(at.parent, hintSource).values();
        helper.assertTrue(at.parent.getName().equals("thrust") && !usage.isEmpty(),
                "Inside command(, the hint should be thrust's argument, got " + at.parent.getName() + " " + usage);

        String command = "on_phys_tick command(thrust 0.5)";
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level);
        List<String> kinds = atThruster.highlight(command, source, null).stream()
                .map(highlight -> highlight.kind() + ":" + highlight.range().get(command)).toList();
        helper.assertTrue(kinds.equals(List.of("EXECUTOR:on_phys_tick", "ARGUMENT:command(", "EXECUTOR:thrust",
                        "ARGUMENT:0.5", "ARGUMENT:)")),
                "on_phys_tick's command should highlight as a command, got " + kinds);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void decodedTreeSuggestsExecutorsGettersAndMappers(GameTestHelper helper) {
        ScriptView<CommandSourceStack> view = decode(helper, Set.of());
        helper.assertTrue(suggest(helper, view, "set_redst").contains("set_redstone"),
                "set_redstone should be suggested, got " + suggest(helper, view, "set_redst"));
        helper.assertTrue(suggest(helper, view, "set_redstone value_of(po").contains("pos"),
                "pos should be suggested inside value_of, got " + suggest(helper, view, "set_redstone value_of(po"));
        helper.assertTrue(suggest(helper, view, "if pos ").contains("=="),
                "== should follow a block position, got " + suggest(helper, view, "if pos "));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void decodedTreeLeavesOutWhatTheConnectedBlocksCannotDo(GameTestHelper helper) {
        ScriptView<CommandSourceStack> stone = decode(helper, Set.of(anywhere(ResourceLocation.withDefaultNamespace("stone"))));
        helper.assertTrue(!suggest(helper, stone, "set_pa").contains("set_page"),
                "set_page is for lecterns and should not be offered for stone");
        ScriptView<CommandSourceStack> lectern = decode(helper, Set.of(anywhere(ZPSMod.resource("data_lectern"))));
        helper.assertTrue(suggest(helper, lectern, "set_pa").contains("set_page"),
                "set_page should be offered for a data lectern");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void decodedTreeReadsArgumentsThatNeedRegistries(GameTestHelper helper) {
        ScriptView<CommandSourceStack> view = decode(helper, Set.of());
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        for (String command : List.of(
                "if block == minecraft:stone set_redstone 1",
                "if held_item == minecraft:stone set_redstone 1",
                "set_recipe minecraft:stone_bricks",
                "if dimension == minecraft:overworld set_redstone 1")) {
            var parse = view.parse(command, source);
            helper.assertTrue(!parse.getReader().canRead() && parse.getExceptions().isEmpty(),
                    "'" + command + "' should parse on a decoded tree, stopped at " + parse.getReader().getCursor()
                            + " " + parse.getExceptions().values());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void decodedTreeHighlightsAsTheServerDoes(GameTestHelper helper) {
        ScriptView<CommandSourceStack> view = decode(helper, Set.of());
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        String command = "if redstone > 3 set_redstone value_of(pos x) else set_redstone 0";
        List<Highlight> client = view.highlight(command, source, null);
        List<Highlight> server = ZPSScripts.get().engine().highlight(command, source, null);
        helper.assertTrue(client.equals(server), "Highlighting should match the server's: " + client + " vs " + server);
        helper.assertTrue(client.stream().noneMatch(highlight -> highlight.kind() == Highlight.Kind.UNPARSED),
                "Nothing should be unparsed in '" + command + "': " + client);
        helper.succeed();
    }
}
