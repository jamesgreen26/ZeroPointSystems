package g_mungus.zps.blockentity.light_pipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link ScriptFile#parse}, the header handling shared by file loading and {@code load_script}. */
public class ScriptFileTest {

    @Test
    void headerSetsSettingsAndIsLeftOut() {
        ScriptFile file = ScriptFile.parse("# 4t REPEAT\nset_redstone 15\nwait 2");
        assertEquals(new ScriptFile("set_redstone 15\nwait 2", 4, true), file);
    }

    @Test
    void headerIsCaseInsensitive() {
        assertEquals(new ScriptFile("x", 16, false), ScriptFile.parse("#16t impulse\nx"));
    }

    @Test
    void headerAloneGivesEmptyScript() {
        assertEquals(new ScriptFile("", 8, true), ScriptFile.parse("# 8t REPEAT"));
    }

    @Test
    void noHeaderKeepsEverything() {
        String script = "# a comment (with brackets) and \"quotes\"\nset_redstone 1";
        assertEquals(new ScriptFile(script, null, null), ScriptFile.parse(script));
    }

    @Test
    void headerOnlyCountsOnTheFirstLine() {
        String script = "set_redstone 1\n# 4t REPEAT";
        assertEquals(new ScriptFile(script, null, null), ScriptFile.parse(script));
    }

    @Test
    void windowsLineEndingsAreNormalised() {
        assertEquals(new ScriptFile("a\nb", 2, false), ScriptFile.parse("# 2t IMPULSE\r\na\r\nb"));
    }

    @Test
    void serializeRoundTrips() {
        ScriptFile file = new ScriptFile("if redstone > 0 set_redstone 15", 4, true);
        assertEquals(file, ScriptFile.parse(file.serialize()));
    }
}
