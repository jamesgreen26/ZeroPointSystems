package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.DenseCableSeparatorBlock;
import g_mungus.zps.block.cableNetwork.GraduatedLeverBlock;
import g_mungus.zps.block.cableNetwork.PanelBlock;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.blockentity.GraduatedLeverBlockEntity;
import g_mungus.zps.blockentity.NetworkTerminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Gametests for {@link g_mungus.zps.mixin.StructureTemplateMixin}.
 *
 * Each test places a (lever + cable) pair via {@code StructureTemplate.placeInWorld}
 * with a non-identity rotation or mirror, then asserts that the mixin correctly
 * re-established the cable network connection using the live world state.
 *
 * GraduatedLeverBlock is used (not SwitchPanelBlock) because it has 1 channel,
 * matching the basic cable's channel count. SwitchPanelBlock has 4 channels and
 * would not connect to a plain cable.
 *
 * Mirror semantics (from StructureTemplate.transform source):
 *   LEFT_RIGHT  → flips Z axis (z′ = −z); FACING: NORTH↔SOUTH
 *   FRONT_BACK  → flips X axis (x′ = −x); FACING: EAST↔WEST
 *
 * Position transform formulas (pivot == offset, worldPos = placeAt + transform(local)):
 *   CW_90:      x′ = −z,  z′ =  x
 *   CW_180:     x′ = −x,  z′ = −z
 *   CCW_90:     x′ =  z,  z′ = −x
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableNetworkGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    // -------------------------------------------------------------------------
    // Block-state helpers
    // -------------------------------------------------------------------------

    /** NORTH-facing GraduatedLever on a wall. Connects to block at pos.south(). */
    private static BlockState northLever() {
        return ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACING, Direction.NORTH)
                .setValue(PanelBlock.FACE, AttachFace.WALL);
    }

    /** EAST-facing GraduatedLever on a wall. Connects to block at pos.west(). */
    private static BlockState eastLever() {
        return ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACING, Direction.EAST)
                .setValue(PanelBlock.FACE, AttachFace.WALL);
    }

    private static BlockState cable() {
        return ModBlocks.CABLE.get().defaultBlockState();
    }

    // -------------------------------------------------------------------------
    // Assertion helper
    // -------------------------------------------------------------------------

    private static void assertConnected(GameTestHelper helper, BlockPos leverRel) {
        helper.assertBlockProperty(leverRel, PanelBlock.CONNECTED, true);
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(leverRel));
        if (!(be instanceof NetworkTerminal terminal)) {
            helper.fail("No NetworkTerminal at " + leverRel);
        } else if (terminal.getTerminalHolder().isEmpty()) {
            helper.fail("Terminal at " + leverRel + " has no connections");
        }
    }

    // -------------------------------------------------------------------------
    // Template builders
    //
    // Temp positions (1,1,x) are away from the placement zone (3,1,x).
    // Cable is placed before lever so the lever sees the cable on onPlace,
    // connects, and saves non-empty terminal NBT — required for BlockEntity.load()
    // to be called during placeInWorld (which fires the mixin).
    // -------------------------------------------------------------------------

    /**
     * Template A: NORTH lever at local (0,0,0), cable at local (0,0,1).
     * FACING=NORTH → connects to pos.south() = local (0,0,1). Size: 1×1×2.
     */
    private static StructureTemplate buildTemplateA(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos leverAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos cableAbs = leverAbs.south();
        level.setBlock(cableAbs, cable(), 3);
        level.setBlock(leverAbs, northLever(), 3);
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, leverAbs, new Vec3i(1, 1, 2), false, null);
        level.setBlock(leverAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(cableAbs, Blocks.AIR.defaultBlockState(), 3);
        return t;
    }

    /**
     * Template B: cable at local (0,0,0), EAST lever at local (1,0,0).
     * FACING=EAST → connects to pos.west() = local (0,0,0). Size: 2×1×1.
     * Used for FRONT_BACK mirror test (x′=−x flips EAST→WEST).
     */
    private static StructureTemplate buildTemplateB(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cableAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos leverAbs = cableAbs.east();
        level.setBlock(cableAbs, cable(), 3);
        level.setBlock(leverAbs, eastLever(), 3);
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, cableAbs, new Vec3i(2, 1, 1), false, null);
        level.setBlock(leverAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(cableAbs, Blocks.AIR.defaultBlockState(), 3);
        return t;
    }

    /** Places {@code t} with origin at abs(3,1,3). */
    private static void placeAt3x3(GameTestHelper helper, StructureTemplate t, StructurePlaceSettings settings) {
        ServerLevel level = helper.getLevel();
        BlockPos placeAt = helper.absolutePos(new BlockPos(3, 1, 3));
        t.placeInWorld(level, placeAt, placeAt, settings, level.getRandom(), 2);
    }

    // -------------------------------------------------------------------------
    // Tests — rotation (all use Template A)
    //
    // Lever always lands at rel (3,1,3); cable lands at the transformed position.
    // -------------------------------------------------------------------------

    /**
     * CW_90: cable (0,0,1) → (−1,0,0) → rel (2,1,3).
     * Lever FACING NORTH→EAST; EAST connects WEST → rel (2,1,3) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void cw90Rotation_panelConnectsViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateA(helper),
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90));
        assertConnected(helper, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    /**
     * CW_180: cable (0,0,1) → (0,0,−1) → rel (3,1,2).
     * Lever FACING NORTH→SOUTH; SOUTH connects NORTH → rel (3,1,2) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void cw180Rotation_panelConnectsViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateA(helper),
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_180));
        assertConnected(helper, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    /**
     * CCW_90: cable (0,0,1) → (1,0,0) → rel (4,1,3).
     * Lever FACING NORTH→WEST; WEST connects EAST → rel (4,1,3) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void ccw90Rotation_panelConnectsViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateA(helper),
                new StructurePlaceSettings().setRotation(Rotation.COUNTERCLOCKWISE_90));
        assertConnected(helper, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Tests — mirror
    // -------------------------------------------------------------------------

    /**
     * LEFT_RIGHT (z′=−z) on Template A.
     * Cable (0,0,1) → (0,0,−1) → rel (3,1,2).
     * Lever FACING NORTH→SOUTH (LEFT_RIGHT flips N/S); SOUTH connects NORTH → rel (3,1,2) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void leftRightMirror_panelConnectsViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateA(helper),
                new StructurePlaceSettings().setMirror(Mirror.LEFT_RIGHT));
        assertConnected(helper, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    /**
     * FRONT_BACK (x′=−x) on Template B (cable at local 0,0,0; EAST lever at local 1,0,0).
     * Lever (1,0,0) → (−1,0,0) → rel (2,1,3), FACING EAST→WEST.
     * Cable (0,0,0) → (0,0,0) → rel (3,1,3).
     * WEST lever connects EAST → rel (3,1,3) ✓. Assert at lever rel (2,1,3).
     */
    @GameTest(template = TEMPLATE)
    public static void frontBackMirror_panelConnectsViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateB(helper),
                new StructurePlaceSettings().setMirror(Mirror.FRONT_BACK));
        assertConnected(helper, new BlockPos(2, 1, 3));
        helper.succeed();
    }

    /**
     * Template D: the separator chain from leverSignalThroughSeparatorsToLamp,
     * captured for rotation testing. Size: 1×2×4 (origin = abs(1,1,1)).
     *
     * Local layout:
     *   (0,0,0)–(0,0,1): AIR
     *   (0,0,2): sep1      FACING=UP
     *   (0,0,3): lever     FACING=SOUTH  → connects NORTH to sep1
     *   (0,1,0): lamp
     *   (0,1,1): converter FACING=NORTH  → output NORTH = lamp
     *   (0,1,2): sep2      FACING=DOWN   → directly above sep1 (facing axis)
     *   (0,1,3): AIR
     *
     * Lever is set to POWER=8 and signal is propagated so block-entity NBT
     * carries both the terminal list (required for mixin) and the signal state.
     */
    private static StructureTemplate buildTemplateD(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin       = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos sep1Abs      = origin.offset(0, 0, 2);
        BlockPos sep2Abs      = origin.offset(0, 1, 2);
        BlockPos converterAbs = origin.offset(0, 1, 1);
        BlockPos leverAbs     = origin.offset(0, 0, 3);
        BlockPos lampAbs      = origin.offset(0, 1, 0);

        // Backbone first, then terminals
        level.setBlock(sep1Abs, ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                .setValue(DenseCableSeparatorBlock.FACING, Direction.UP), 3);
        level.setBlock(sep2Abs, ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                .setValue(DenseCableSeparatorBlock.FACING, Direction.DOWN), 3);
        level.setBlock(converterAbs, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, Direction.NORTH), 3);
        level.setBlock(leverAbs, ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACING, Direction.SOUTH)
                .setValue(PanelBlock.FACE, AttachFace.WALL), 3);
        level.setBlock(lampAbs, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);

        // Lever stays at POWER=0 (default) — terminal NBT is still populated because
        // the lever connects to sep1 on placement, which triggers defineTerminals.
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, origin, new Vec3i(1, 2, 4), false, null);

        level.setBlock(sep1Abs,      Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(sep2Abs,      Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(converterAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(leverAbs,     Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(lampAbs,      Blocks.AIR.defaultBlockState(), 3);
        return t;
    }

    /**
     * Template C: lamp at local (0,0,0), converter at local (0,0,1) FACING=NORTH,
     * cable at local (1,0,1), lever at local (2,0,1) FACING=EAST, POWER=8. Size: 3×1×2.
     * Used for signal-through-rotation tests.
     */
    private static StructureTemplate buildTemplateC(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lampAbs      = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos converterAbs = lampAbs.south();
        BlockPos cableAbs     = converterAbs.east();
        BlockPos leverAbs     = cableAbs.east();

        level.setBlock(cableAbs, cable(), 3);
        level.setBlock(converterAbs,
                ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                        .setValue(TransformerBlock.FACING, Direction.NORTH), 3);
        level.setBlock(leverAbs, eastLever(), 3);
        level.setBlock(lampAbs, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);

        // Set POWER=8 and propagate so signal state is captured in block entity NBT
        level.setBlock(leverAbs, level.getBlockState(leverAbs).setValue(GraduatedLeverBlock.POWER, 8), 3);
        BlockEntity leverBe = level.getBlockEntity(leverAbs);
        if (leverBe instanceof GraduatedLeverBlockEntity glbe) {
            glbe.updateSignal(level, Channels.MAIN);
        }

        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, lampAbs, new Vec3i(3, 1, 2), false, null);

        level.setBlock(lampAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(converterAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(cableAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(leverAbs, Blocks.AIR.defaultBlockState(), 3);
        return t;
    }

    // -------------------------------------------------------------------------
    // Signal integration test
    // -------------------------------------------------------------------------

    /**
     * End-to-end signal test: GraduatedLever at POWER=8 sends signal through a
     * cable to a RedstoneConverter, which powers an adjacent Redstone Lamp.
     *
     * TransformerBlock semantics:
     *   - FACING direction = redstone output face (lamp must be here)
     *   - All other faces = cable connection (getChannelCountForConnection returns 0 for FACING side)
     *
     * Layout (all at y=1):
     *   (3,1,2) lamp
     *   (3,1,3) converter  FACING=NORTH → lamp is NORTH of converter (output side)
     *   (4,1,3) cable      EAST of converter (not FACING) → cable connection allowed ✓
     *   (5,1,3) lever      FACING=EAST  → connecting pos = pos.west() = (4,1,3) = cable ✓
     *
     * Signal path: lever.POWER=8 → updateSignal → converter.receiveSignal(8)
     *              → updateNeighborsAt(converterPos) + updateNeighborsAt(lampPos)
     *              → lamp queries converter via getDirectSignal(direction=SOUTH) == FACING.getOpposite() ✓
     */
    @GameTest(template = TEMPLATE)
    public static void leverSignalPowersLampViaCable(GameTestHelper helper) {
        BlockPos lampPos      = new BlockPos(3, 1, 2);
        BlockPos converterPos = new BlockPos(3, 1, 3);
        BlockPos cablePos     = new BlockPos(4, 1, 3);
        BlockPos leverPos     = new BlockPos(5, 1, 3);

        // Cable first so lever and converter connect immediately on their onPlace calls
        helper.setBlock(cablePos, cable());
        helper.setBlock(leverPos, eastLever());
        helper.setBlock(converterPos,
                ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                        .setValue(TransformerBlock.FACING, Direction.NORTH));
        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());

        // Sanity-check: lever must be connected through the cable to the converter
        helper.assertBlockProperty(leverPos, PanelBlock.CONNECTED, true);

        // Set POWER=8 and propagate the signal (mirrors GraduatedLeverBlock.use() logic)
        ServerLevel level = helper.getLevel();
        BlockPos leverAbs = helper.absolutePos(leverPos);
        level.setBlock(leverAbs, level.getBlockState(leverAbs).setValue(GraduatedLeverBlock.POWER, 8), 3);
        BlockEntity be = level.getBlockEntity(leverAbs);
        if (be instanceof GraduatedLeverBlockEntity glbe) {
            glbe.updateSignal(level, Channels.MAIN);
        }

        // The converter fires updateNeighborsAt on itself and its FACING pos (lamp),
        // causing the lamp to query getDirectSignal → receives currentSignal=8 → LIT
        helper.assertBlockProperty(lampPos, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }

    /**
     * CW_90 rotation on Template C (full signal path).
     *
     * CW_90 transform (x′=−z, z′=x), placed at origin rel (3,1,3):
     *   lamp      local (0,0,0) → rel (3,1,3)
     *   converter local (0,0,1) → rel (2,1,3)  FACING NORTH→EAST
     *   cable     local (1,0,1) → rel (2,1,4)
     *   lever     local (2,0,1) → rel (2,1,5)  FACING EAST→SOUTH
     *
     * Lever SOUTH → connects NORTH → (2,1,4) = cable ✓
     * Converter FACING=EAST → output EAST → (3,1,3) = lamp ✓
     * Cable is SOUTH of converter (not FACING direction) → cable connection allowed ✓
     */
    @GameTest(template = TEMPLATE)
    public static void cw90Rotation_signalPowersLampViaCable(GameTestHelper helper) {
        placeAt3x3(helper, buildTemplateC(helper),
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90));

        BlockPos leverRel = new BlockPos(2, 1, 5);
        BlockPos lampRel  = new BlockPos(3, 1, 3);

        // Network must have reconnected via mixin after rotation
        assertConnected(helper, leverRel);

        // Propagate signal from the rotated lever
        ServerLevel level = helper.getLevel();
        BlockPos leverAbs = helper.absolutePos(leverRel);
        BlockEntity be = level.getBlockEntity(leverAbs);
        if (be instanceof GraduatedLeverBlockEntity glbe) {
            glbe.updateSignal(level, Channels.MAIN);
        }

        helper.assertBlockProperty(lampRel, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }

    /**
     * End-to-end signal test through a vertical DenseCableSeparator chain.
     *
     * Layout (ROTATION=0 on both separators):
     *   y=1: lever  (3,1,4) FACING=SOUTH → connects NORTH to sep1
     *        sep1   (3,1,3) FACING=UP
     *   y=2: sep2   (3,2,3) FACING=DOWN  ← directly above sep1, facing axis connection
     *        converter (3,2,2) FACING=NORTH ← NORTH of sep2, cable from its south side
     *        lamp   (3,2,1)               ← NORTH of converter (FACING output direction)
     *
     * Channel routing:
     *   MAIN enters sep1 from SOUTH → getChannelForNeighborPos: FACING=UP, c0=SOUTH,
     *     offset=SOUTH==c0 → QUAD_1 (rotation=0)
     *   QUAD_1 travels sep1→sep2 on 4-ch facing axis (both report 4 channels)
     *   sep2 receives QUAD_1 on Y axis → toQuad(QUAD_1)=QUAD_1;
     *     routes north (FACING=DOWN, c0=NORTH, index=0 for QUAD_1) → converter
     *   Sep2 perpendicular (1 ch) == converter non-FACING south side (1 ch) ✓
     *   receiveSignal(8, QUAD_1) → lamp LIT ✓
     */
    @GameTest(template = TEMPLATE)
    public static void leverSignalThroughSeparatorsToLamp(GameTestHelper helper) {
        BlockPos sep1Pos      = new BlockPos(3, 1, 3);
        BlockPos sep2Pos      = new BlockPos(3, 2, 3);
        BlockPos converterPos = new BlockPos(3, 2, 2);
        BlockPos lampPos      = new BlockPos(3, 2, 1);
        BlockPos leverPos     = new BlockPos(3, 1, 4);

        // Separators first so terminals connect to an established backbone
        helper.setBlock(sep1Pos,
                ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                        .setValue(DenseCableSeparatorBlock.FACING, Direction.UP));
        helper.setBlock(sep2Pos,
                ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                        .setValue(DenseCableSeparatorBlock.FACING, Direction.DOWN));
        helper.setBlock(converterPos,
                ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                        .setValue(TransformerBlock.FACING, Direction.NORTH));
        helper.setBlock(leverPos,
                ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                        .setValue(PanelBlock.FACING, Direction.SOUTH)
                        .setValue(PanelBlock.FACE, AttachFace.WALL));
        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());

        // Lever must be connected through the separator chain to the converter
        helper.assertBlockProperty(leverPos, PanelBlock.CONNECTED, true);

        // Set POWER=8 and propagate
        ServerLevel level = helper.getLevel();
        BlockPos leverAbs = helper.absolutePos(leverPos);
        level.setBlock(leverAbs, level.getBlockState(leverAbs).setValue(GraduatedLeverBlock.POWER, 8), 3);
        BlockEntity be = level.getBlockEntity(leverAbs);
        if (be instanceof GraduatedLeverBlockEntity glbe) {
            glbe.updateSignal(level, Channels.MAIN);
        }

        helper.assertBlockProperty(lampPos, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }

    /**
     * CW_90 rotation on Template D (full separator chain).
     *
     * Placed at origin rel (5,1,3); CW_90 transform: x′=−z, z′=x.
     *
     *   sep1      local(0,0,2) → rel(3,1,3)  FACING=UP,   ROTATION=3*
     *   sep2      local(0,1,2) → rel(3,2,3)  FACING=DOWN, ROTATION=1†
     *   converter local(0,1,1) → rel(4,2,3)  FACING=EAST  (NORTH→EAST)
     *   lamp      local(0,1,0) → rel(5,2,3)
     *   lever     local(0,0,3) → rel(2,1,3)  FACING=WEST  (SOUTH→WEST)
     *
     * *  UP Y-axis: steps=CW_90→1, reversed→(4-1)%4=3; ROTATION=(0+3)%4=3
     * †  DOWN Y-axis: steps=CW_90→1, no reversal;      ROTATION=(0+1)%4=1
     *
     * Channel routing after rotation:
     *   lever WEST of sep1 → offset=WEST matches facing.cross(c0).multiply(-1) with ROTATION=3
     *     → QUAD_1; travels up facing axis to sep2
     *   sep2 ROTATION=1: index=(8+1-1-1)%4=3 → case3=facing.cross(c0)=DOWN.cross(NORTH)=EAST
     *     → routes QUAD_1 EAST to converter ✓
     *   converter FACING=EAST → output EAST → lamp at (5,2,3) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void cw90Rotation_separatorChainPowersLamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos placeAt = helper.absolutePos(new BlockPos(5, 1, 3));
        buildTemplateD(helper).placeInWorld(
                level, placeAt, placeAt,
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90),
                level.getRandom(), 2);

        BlockPos leverRel = new BlockPos(2, 1, 3);
        BlockPos lampRel  = new BlockPos(5, 2, 3);

        // Network must reconnect via mixin; lever starts at POWER=0 so lamp is off
        assertConnected(helper, leverRel);
        helper.assertBlockProperty(lampRel, RedstoneLampBlock.LIT, false);

        // Use a fake player to click the top half of the lever's front face.
        // After CW_90 the lever is FACING=WEST; isFrontFace requires direction=WEST.
        // getQuadrant(WALL, FACING=WEST): u=z=0.5, v=y=0.75 → v>0.5 → top half → POWER 0→1.
        BlockPos leverAbs = helper.absolutePos(leverRel);
        BlockState leverState = level.getBlockState(leverAbs);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(leverAbs).add(0, 0.25, 0),
                Direction.WEST,
                leverAbs,
                false
        );
        leverState.getBlock().use(leverState, level, leverAbs,
                FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND, hit);

        // POWER=1 signal must have propagated through the separator chain to light the lamp
        helper.assertBlockProperty(lampRel, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }

    /**
     * Template E: inverted separator chain — lever enters the TOP separator,
     * redstone output exits the BOTTOM separator on QUAD_3. Size: 1×2×4.
     *
     * Local layout (origin = abs(1,1,1)):
     *   (0,0,0): lamp
     *   (0,0,1): converter  FACING=NORTH  (output NORTH = lamp)
     *   (0,0,2): bottom_sep FACING=UP
     *   (0,1,2): top_sep    FACING=DOWN   (above bottom_sep; facing axis between them)
     *   (0,1,3): lever      FACING=SOUTH  (connects NORTH to top_sep)
     *
     * Channel routing: QUAD_3 throughout.
     *   top_sep  FACING=DOWN, c0=NORTH: SOUTH side = c0.multiply(-1) → 1+((0+2)%4)=QUAD_3
     *   bottom_sep FACING=UP, c0=SOUTH: NORTH side = c0.multiply(-1) → 1+((0+2)%4)=QUAD_3
     */
    private static StructureTemplate buildTemplateE(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin      = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos lampAbs      = origin.offset(0, 0, 0);
        BlockPos converterAbs = origin.offset(0, 0, 1);
        BlockPos botSepAbs    = origin.offset(0, 0, 2);
        BlockPos topSepAbs    = origin.offset(0, 1, 2);
        BlockPos leverAbs     = origin.offset(0, 1, 3);

        // Backbone first, then terminals
        level.setBlock(botSepAbs, ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                .setValue(DenseCableSeparatorBlock.FACING, Direction.UP), 3);
        level.setBlock(topSepAbs, ModBlocks.DENSE_CABLE_SEPARATOR.get().defaultBlockState()
                .setValue(DenseCableSeparatorBlock.FACING, Direction.DOWN), 3);
        level.setBlock(converterAbs, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, Direction.NORTH), 3);
        level.setBlock(leverAbs, ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACING, Direction.SOUTH)
                .setValue(PanelBlock.FACE, AttachFace.WALL), 3);
        level.setBlock(lampAbs, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);

        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, origin, new Vec3i(1, 2, 4), false, null);

        level.setBlock(lampAbs,      Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(converterAbs, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(botSepAbs,    Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(topSepAbs,    Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(leverAbs,     Blocks.AIR.defaultBlockState(), 3);
        return t;
    }

    /**
     * CW_90 rotation on Template E (inverted chain, QUAD_3).
     *
     * Placed at origin rel (5,1,3); CW_90 transform: x′=−z, z′=x.
     *
     *   bottom_sep local(0,0,2) → rel(3,1,3)  FACING=UP,   ROTATION=3*
     *   top_sep    local(0,1,2) → rel(3,2,3)  FACING=DOWN, ROTATION=1†
     *   converter  local(0,0,1) → rel(4,1,3)  FACING=EAST  (NORTH→EAST)
     *   lamp       local(0,0,0) → rel(5,1,3)
     *   lever      local(0,1,3) → rel(2,2,3)  FACING=WEST  (SOUTH→WEST)
     *
     * *  UP Y-axis: CW_90 steps=1, reversed→(4-1)%4=3; ROTATION=(0+3)%4=3
     * †  DOWN Y-axis: CW_90 steps=1, no reversal;       ROTATION=(0+1)%4=1
     *
     * QUAD_3 channel routing after rotation:
     *   lever WEST of top_sep: offset=WEST matches facing.cross(c0).multiply(-1)
     *     with FACING=DOWN, c0=NORTH, ROTATION=1 → 1+((1+1)%4)=QUAD_3
     *   QUAD_3 travels down facing axis (top→bottom sep); toQuad(QUAD_3)=QUAD_3
     *   bottom_sep ROTATION=3: EAST neighbor matches facing.cross(c0)
     *     with FACING=UP, c0=SOUTH, ROTATION=3 → 1+((3+3)%4)=QUAD_3 → routes to converter ✓
     *   converter FACING=EAST → output EAST → lamp at (5,1,3) ✓
     */
    @GameTest(template = TEMPLATE)
    public static void cw90Rotation_leverToTopSep_quad3PowersLamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos placeAt = helper.absolutePos(new BlockPos(5, 1, 3));
        buildTemplateE(helper).placeInWorld(
                level, placeAt, placeAt,
                new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90),
                level.getRandom(), 2);

        BlockPos leverRel = new BlockPos(2, 2, 3);
        BlockPos lampRel  = new BlockPos(5, 1, 3);

        // Network must reconnect via mixin; lever starts at POWER=0 so lamp is off
        assertConnected(helper, leverRel);
        helper.assertBlockProperty(lampRel, RedstoneLampBlock.LIT, false);

        // Lever after CW_90 is FACING=WEST; click top half of the WEST face → POWER 0→1
        BlockPos leverAbs = helper.absolutePos(leverRel);
        BlockState leverState = level.getBlockState(leverAbs);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(leverAbs).add(0, 0.25, 0),
                Direction.WEST,
                leverAbs,
                false
        );
        leverState.getBlock().use(leverState, level, leverAbs,
                FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND, hit);

        // POWER=1 on QUAD_3 must light the lamp via the inverted separator chain
        helper.assertBlockProperty(lampRel, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }
}
