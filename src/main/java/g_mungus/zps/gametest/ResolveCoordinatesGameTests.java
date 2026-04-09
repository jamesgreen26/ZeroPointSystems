package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Gametests for {@link ScriptTerminalBlockEntity#resolveCoordinates}.
 *
 * Covers: plain passthrough, space-separated {@code ~} triplets, paren-wrapped
 * triplets (the new delimiter handling), multiple triplets in one command, and
 * {@code ^} local coordinates.
 *
 * All tests use a flat 7×4×7 stone platform. The terminal is assumed to face
 * NORTH, so the command source faces SOUTH (yaw 0). In that orientation:
 * {@code ^0 ^0 ^1} is one block forward (+Z).
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ResolveCoordinatesGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static BlockState northFacing() {
        return ModBlocks.SCRIPT_TERMINAL.get().defaultBlockState()
                .setValue(ScriptTerminalBlock.FACING, Direction.NORTH);
    }

    private static String resolve(GameTestHelper helper, BlockPos absPos, String command) {
        return ScriptTerminalBlockEntity.resolveCoordinates(
                command, helper.getLevel(), absPos, northFacing());
    }

    private static void assertEq(GameTestHelper helper, String expected, String actual) {
        if (!actual.equals(expected)) {
            helper.fail("expected '" + expected + "', got '" + actual + "'");
        }
    }

    // -------------------------------------------------------------------------
    // ~ relative coordinates
    // -------------------------------------------------------------------------

    /** Commands with no coordinate tokens should pass through unchanged. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_noCoordinates_unchanged(GameTestHelper helper) {
        String command = "set_redstone 7";
        assertEq(helper, command, resolve(helper, helper.absolutePos(new BlockPos(3, 1, 3)), command));
        helper.succeed();
    }

    /** Space-separated {@code ~} triplet — the original supported form. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_relativeSpaceSep_resolvedToAbsolute(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "~1 ~0 ~0");
        String expected = (abs.getX() + 1) + " " + abs.getY() + " " + abs.getZ();
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /** {@code (} before the first coordinate should be preserved in output. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_relativeWithLeadingParen_preservesParen(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "teleport(~1 ~0 ~0)");
        String expected = "teleport(" + (abs.getX() + 1) + " " + abs.getY() + " " + abs.getZ() + ")";
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /** Z-offset inside parens, verifying {@code )} is also preserved. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_relativeZOffsetInParens_parenPreserved(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "tp(~0 ~0 ~2)");
        String expected = "tp(" + abs.getX() + " " + abs.getY() + " " + (abs.getZ() + 2) + ")";
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /** Two separate triplets in one command should both be resolved. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_multipleTriplets_bothResolved(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "from ~0 ~0 ~0 to ~1 ~1 ~1");
        String expected = "from " + abs.getX() + " " + abs.getY() + " " + abs.getZ()
                + " to " + (abs.getX() + 1) + " " + (abs.getY() + 1) + " " + (abs.getZ() + 1);
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /** Two paren-wrapped triplets in one command. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_multipleParenTriplets_bothResolved(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "from(~0 ~0 ~0)to(~1 ~0 ~0)");
        String expected = "from(" + abs.getX() + " " + abs.getY() + " " + abs.getZ()
                + ")to(" + (abs.getX() + 1) + " " + abs.getY() + " " + abs.getZ() + ")";
        assertEq(helper, expected, result);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // ^ local coordinates
    // -------------------------------------------------------------------------

    /** {@code ^0 ^0 ^0} is the identity case — no offset in any direction. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_localNoOffset_resolvedToSamePos(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "^0 ^0 ^0");
        String expected = abs.getX() + " " + abs.getY() + " " + abs.getZ();
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /**
     * {@code ^0 ^0 ^1} with a NORTH-facing terminal: source faces SOUTH (yaw 0),
     * so one step forward is +Z.
     */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_localForwardOneStep_northFacing(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "^0 ^0 ^1");
        String expected = abs.getX() + " " + abs.getY() + " " + (abs.getZ() + 1);
        assertEq(helper, expected, result);
        helper.succeed();
    }

    /** Local coords inside parens should also have parens preserved. */
    @GameTest(template = TEMPLATE)
    public static void resolveCoords_localInParens_parenPreserved(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 3));
        String result = resolve(helper, abs, "tp(^0 ^0 ^0)");
        String expected = "tp(" + abs.getX() + " " + abs.getY() + " " + abs.getZ() + ")";
        assertEq(helper, expected, result);
        helper.succeed();
    }
}
