package g_mungus.zps.gametest;

import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.codec.ScriptTreeCodec;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.commands.sync.ZPSHostCodec;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
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

    /** Offers a restricted node where one of {@code connected} is among its blocks, as the client does. */
    private record Host(Set<ResourceLocation> connected) implements ScriptViewHost<CommandSourceStack> {
        @Override
        public Object hostContext(CommandSourceStack source) {
            return this;
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            return applicability instanceof BlockApplicability blocks && !blocks.appliesToAny(connected)
                    ? Match.NONE
                    : Match.EXPLICIT;
        }

        @Override
        public String defaultNamespace() {
            return ZPSMod.MOD_ID;
        }
    }

    private static ScriptView<CommandSourceStack> decode(GameTestHelper helper, Set<ResourceLocation> connected) {
        ServerLevel level = helper.getLevel();
        CommandBuildContext buildContext = CommandBuildContext.simple(level.registryAccess(), level.enabledFeatures());
        try {
            return new ScriptTreeCodec(new ZPSHostCodec(buildContext)).decode(
                    new DataInputStream(new ByteArrayInputStream(ZPSScripts.get().encodedTree())),
                    new Host(connected), ZPSScriptTypes.all());
        } catch (IOException e) {
            throw new IllegalStateException("The script tree did not decode", e);
        }
    }

    private static List<String> suggest(GameTestHelper helper, ScriptView<CommandSourceStack> view, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        return view.suggest(command, command.length(), source, null).join().getList().stream()
                .map(Suggestion::getText).toList();
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
        ScriptView<CommandSourceStack> stone = decode(helper, Set.of(ResourceLocation.withDefaultNamespace("stone")));
        helper.assertTrue(!suggest(helper, stone, "set_pa").contains("set_page"),
                "set_page is for lecterns and should not be offered for stone");
        ScriptView<CommandSourceStack> lectern = decode(helper, Set.of(ZPSMod.resource("data_lectern")));
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
