package g_mungus.zps.client.ponder;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.DenseCableSeparatorBlock;
import g_mungus.zps.block.cableNetwork.GraduatedLeverBlock;
import g_mungus.zps.block.cableNetwork.PanelBlock;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.RedstoneConverterBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.DataLecternBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import g_mungus.zps.client.ponder.api.PonderExtras;
import g_mungus.zps.client.ponder.api.ReactorGlowElement;
import g_mungus.zps.client.ponder.api.SceneViewElement;
import g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene.*;
import g_mungus.zps.client.screens.ScriptTerminalScreen;
import g_mungus.zps.item.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.createmod.ponder.foundation.instruction.RotateSceneInstruction;
import net.createmod.ponder.foundation.instruction.ShowInputInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ZPSPonderScenes {
    public static void cableTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("cable", "Cables");
        builder.showBasePlate();
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(0,1,0,7,1,7), Direction.DOWN);
        builder.overlay().showText(60).text("Cables don't do anything on their own.");
        builder.idle(60);

        builder.world().showSection(util.select().fromTo(0,2,0,7,2,7), Direction.DOWN);
        builder.overlay().showText(60).text("Add Redstone Converters to send a redstone signal.");
        builder.idle(40);

        builder.world().showSection(util.select().fromTo(0,3,0,7,3,7), Direction.DOWN);
        builder.idle(40);

        builder.world().toggleRedstonePower(util.select().fromTo(0,3,0,7,3,7));
        builder.idle(20);
    }

    public static void energyTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("energy", "Energy Cables");
        builder.showBasePlate();
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(0,1,0,7,1,7), Direction.DOWN);
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(5,2,0,7,2,7), Direction.DOWN);
        builder.idle(20);
        builder.overlay().showText(60).text("Stepup Transformers draw FE from their adjacent block...");
        builder.idle(10);
        builder.effects().emitParticles(new Vec3(5.5, 3.5, 1.5), builder.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, -1, 0)), 2, 20);


        builder.idle(50);

        builder.world().showSection(util.select().fromTo(0,2,0,3,2,7), Direction.DOWN);
        builder.overlay().showText(60).text("...and deposit the FE to connected Stepdown Transformers.");
        builder.idle(10);
        builder.effects().emitParticles(new Vec3(1.5, 3.5, 5.5), builder.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, 1, 0)), 2, 20);

        builder.idle(50);
    }

    public static void energyExplodeTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("energy_explode", "Overvolting a Converter");
        builder.showBasePlate();
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(0,1,0,7,1,7), Direction.DOWN);
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(5,2,0,7,2,7), Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(util.select().fromTo(0,2,0,3,2,7), Direction.DOWN);
        builder.idle(20);
        builder.overlay().showText(60).text("Be careful not to supply FE to a Redstone Converter...");
        builder.idle(60);
        builder.effects().emitParticles(new Vec3(5.5, 3.5, 1.5), builder.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, -1, 0)), 2, 20);

        builder.idle(5);

        builder.effects().emitParticles(new Vec3(1.5, 3.5, 5.5), builder.effects().simpleParticleEmitter(ParticleTypes.EXPLOSION_EMITTER, new Vec3(0, 0, 0)), 1, 1);
        builder.world().destroyBlock(new BlockPos(1, 2, 5));
        builder.world().destroyBlock(new BlockPos(1, 1, 5));
        builder.idle(30);
        builder.overlay().showText(60).text("Redstone Converters cannot handle the higher voltage, and will explode.");
        builder.idle(60);
    }

    public static void insulationTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("insulation", "Cable Insulation");
        builder.world().showSection(util.select().everywhere(), Direction.DOWN);
        builder.idle(20);

        builder.overlay().showText(80).text("Bare cables will always connect, and sometimes this is not wanted.");

        builder.idle(30);

        builder.overlay().showOutline(PonderPalette.RED, "a", util.select().fromTo(3, 1, 1, 3, 2, 1), 50);
        builder.overlay().showOutline(PonderPalette.RED, "b", util.select().fromTo(3, 1, 3, 3, 2, 3), 50);
        builder.overlay().showOutline(PonderPalette.RED, "c", util.select().fromTo(3, 1, 5, 3, 2, 5), 50);
        builder.idle(50);

        BlockState eastWestCable = ModBlocks.CABLE.get().defaultBlockState().setValue(CableBlock.EAST, true).setValue(CableBlock.WEST, true);
        BlockState eastWestInsulatedCable = ModBlocks.CABLE.get().defaultBlockState().setValue(CableBlock.EAST, true).setValue(CableBlock.WEST, true).setValue(CableBlock.INSULATION_TYPE, InsulationType.INSULATION);
        BlockState northSouthCable = ModBlocks.CABLE.get().defaultBlockState().setValue(CableBlock.NORTH, true).setValue(CableBlock.SOUTH, true);


        builder.world().setBlock(new BlockPos(3, 1, 1), eastWestCable, false);
        builder.world().setBlock(new BlockPos(3, 1, 3), eastWestCable, false);
        builder.world().setBlock(new BlockPos(3, 1, 5), eastWestCable, false);
        builder.world().setBlock(new BlockPos(3, 2, 1), northSouthCable, false);
        builder.world().setBlock(new BlockPos(3, 2, 3), northSouthCable, false);
        builder.world().setBlock(new BlockPos(3, 2, 5), northSouthCable, false);
        builder.world().hideSection(util.select().fromTo(3, 2, 0, 3, 2, 7), Direction.UP);

        builder.idle(30);
        builder.overlay().showText(80).text("Apply Cable Insulation to cable blocks to prevent them from making new connections.");

        builder.idle(10);
        builder.overlay().showControls(util.vector().topOf(3, 1, 1), Pointing.DOWN, 10).rightClick().withItem(ModItems.CABLE_INSULATION.get().getDefaultInstance());
        builder.world().setBlock(new BlockPos(3, 1, 1), eastWestInsulatedCable, true);
        builder.idle(15);
        builder.overlay().showControls(util.vector().topOf(3, 1, 3), Pointing.DOWN, 10).rightClick().withItem(ModItems.CABLE_INSULATION.get().getDefaultInstance());
        builder.world().setBlock(new BlockPos(3, 1, 3), eastWestInsulatedCable, true);
        builder.idle(15);
        builder.overlay().showControls(util.vector().topOf(3, 1, 5), Pointing.DOWN, 10).rightClick().withItem(ModItems.CABLE_INSULATION.get().getDefaultInstance());
        builder.world().setBlock(new BlockPos(3, 1, 5), eastWestInsulatedCable, true);
        builder.idle(30);
        builder.world().showSection(util.select().fromTo(3, 2, 0, 3, 2, 7), Direction.DOWN);
        builder.idle(30);
    }

    public static void denseCablesTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("dense_cables", "Dense Cables");
        builder.showBasePlate();
        builder.idle(5);

        builder.world().showSection(util.select().fromTo(2,1,0,2,1,7), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(4,1,0,4,1,7), Direction.DOWN);

        builder.world().showSection(util.select().fromTo(1,1,3,1,1,3), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(5,1,3,5,1,3), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(0,2,3,7,2,3), Direction.DOWN);

        builder.overlay().showText(60).text("Tight spaces can be tricky.");
        builder.idle(60);
        builder.world().hideSection(util.select().fromTo(1,1,3,2,1,3), Direction.UP);
        builder.world().hideSection(util.select().fromTo(4,1,3,5,1,3), Direction.UP);
        builder.world().hideSection(util.select().fromTo(0,2,3,7,2,3), Direction.UP);
        builder.rotateCameraY(-45);
        builder.idle(10);
        builder.overlay().showText(60).text("Dense Cables can transmit 4 signals through a single block.");
        builder.idle(10);
        builder.world().showSection(util.select().fromTo(3,1,2,3,1,4), Direction.DOWN);
        builder.idle(60);
        builder.overlay().showText(80).text("Use Dense Cable Separators to merge multiple regular cables into a Dense Cable.");
        builder.idle(30);
        builder.world().showSection(util.select().fromTo(3,1,1,3,1,1), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(3,1,5,3,1,5), Direction.DOWN);
        builder.world().setBlock(new BlockPos(2,1,1), ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState().setValue(RedstoneConverterBlock.FACING, Direction.NORTH).setValue(RedstoneConverterBlock.EAST, true), false);
        builder.world().setBlock(new BlockPos(2,1,5), ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState().setValue(RedstoneConverterBlock.FACING, Direction.SOUTH).setValue(RedstoneConverterBlock.EAST, true), false);

        builder.world().setBlock(new BlockPos(4,1,1), ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState().setValue(RedstoneConverterBlock.FACING, Direction.NORTH).setValue(RedstoneConverterBlock.WEST, true), false);
        builder.world().setBlock(new BlockPos(4,1,5), ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState().setValue(RedstoneConverterBlock.FACING, Direction.SOUTH).setValue(RedstoneConverterBlock.WEST, true), false);
        builder.idle(60);
        builder.world().showSection(util.select().fromTo(1,1,3,2,1,3), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(4,1,3,5,1,3), Direction.DOWN);
        builder.world().showSection(util.select().fromTo(0,2,3,7,2,3), Direction.DOWN);
        builder.idle(5);
        builder.rotateCameraY(45);
        builder.idle(25);
        builder.world().toggleRedstonePower(util.select().fromTo(2,1,0,2,1,7));
        builder.idle(10);
        builder.world().toggleRedstonePower(util.select().fromTo(2,1,0,2,1,7));
        builder.idle(10);
        builder.world().toggleRedstonePower(util.select().fromTo(4,1,0,4,1,7));
        builder.idle(10);
        builder.world().toggleRedstonePower(util.select().fromTo(4,1,0,4,1,7));
        builder.idle(20);

        builder.addKeyframe();

        builder.idle(10);

        builder.overlay().showText(60).text("Dense Cable Separators can be rotated with Shift + R-Click");

        builder.idle(75);

        InputWindowElement inputWindowElement = new InputWindowElement(util.vector().topOf(3, 1, 1), Pointing.DOWN);
        builder.addInstruction(new ShowInputInstruction(inputWindowElement, 16));
        inputWindowElement.builder().rightClick().whileSneaking();

        builder.idle(8);

        builder.world().cycleBlockProperty(new BlockPos(3, 1, 1), DenseCableSeparatorBlock.ROTATION);

        builder.idle(20);

        InputWindowElement secondInputWindowElement = new InputWindowElement(util.vector().topOf(3, 1, 1), Pointing.DOWN);
        builder.addInstruction(new ShowInputInstruction(secondInputWindowElement, 16));
        secondInputWindowElement.builder().rightClick().whileSneaking();

        builder.idle(8);

        builder.world().cycleBlockProperty(new BlockPos(3, 1, 1), DenseCableSeparatorBlock.ROTATION);

        builder.idle(24);

        builder.overlay().showText(50).text("This changes the signal routing.");

        builder.idle(50);

        var firstDiagonalPair = util.select().position(4, 1, 0)
                .add(util.select().position(2, 1, 6));
        var secondDiagonalPair = util.select().position(2, 1, 0)
                .add(util.select().position(4, 1, 6));

        builder.world().toggleRedstonePower(firstDiagonalPair);
        builder.idle(10);
        builder.world().toggleRedstonePower(firstDiagonalPair);
        builder.idle(10);
        builder.world().toggleRedstonePower(secondDiagonalPair);
        builder.idle(10);
        builder.world().toggleRedstonePower(secondDiagonalPair);
        builder.idle(20);
    }

    public static void octoControllerTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("octo_controller", "Octo-Controller");
        builder.world().showSection(util.select().everywhere(), Direction.UP);
        builder.idle(5);

        ElementLink<EntityElement> piglin = builder.world().createEntity(level -> BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:piglin")).create(level));
        builder.world().modifyEntity(piglin, entity -> {
            if (entity instanceof AbstractPiglin it) {
                it.setImmuneToZombification(true);
            }
            entity.moveTo(3.5, 2, 1);
        });
        builder.overlay().showText(60).text("Interact with an Octo-Controller to control it.");

        builder.idle(30);
        builder.world().modifyEntity(piglin, entity -> {
            if (entity instanceof AbstractPiglin it) {
                it.swing(InteractionHand.MAIN_HAND);
                entity.moveTo(3.5, 2, 1.5);

                Entity seat = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:armor_stand")).create(entity.level());
                if (seat instanceof ArmorStand) {
                    seat.setInvisible(true);
                    seat.setNoGravity(true);

                    entity.startRiding(seat, true);
                }
            }
        });
        builder.idle(40);
        builder.overlay().showText(60).text("Use arrow keys and WASD to control the 8 redstone outputs.");
        builder.idle(20);
        builder.world().toggleRedstonePower(util.select().position(1, 3, 5));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(1, 3, 5));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(4, 3, 6));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(4, 3, 6));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(2, 3, 6));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(2, 3, 6));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(5, 3, 5));
        builder.idle(8);
        builder.world().toggleRedstonePower(util.select().position(5, 3, 5));
        builder.idle(20);
    }


    public static void dataCableTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 7);
        builder.title("data_cable", "Data Cables");
        builder.showBasePlate();

        PonderExtras.modifyBlockStates(builder, util, state -> {
            if (state.is(ModBlocks.DATA_CABLE.get()) && (
                    state.getValue(CableBlock.EAST) ||
                    state.getValue(CableBlock.SOUTH)||
                    state.getValue(CableBlock.DOWN))
            ) {
                return state.setValue(CableBlock.NORTH, false);
            } else {
                return state;
            }
        });

        builder.world().showSection(
                PonderExtras.selectBlocks(builder, util, ModBlocks.DATA_CABLE.get()),
                Direction.UP
        );

        builder.idle(5);
        builder.overlay().showText(60).text("Data Cables send information as text between compatible blocks.");
        builder.idle(60);
        builder.overlay().showText(60).text("A minimum setup has an input and output.");
        builder.idle(20);
        builder.world().showSection(util.select().position(4, 1, 2), Direction.DOWN);
        builder.world().setBlock(new BlockPos(4, 1, 3), ModBlocks.DATA_CABLE.get().defaultBlockState().setValue(CableBlock.NORTH, true).setValue(CableBlock.SOUTH, true), false);

        builder.idle(20);
        builder.world().showSection(
                PonderExtras.selectBlocks(builder, util, ModBlocks.TEXT_DISPLAY.get()),
                Direction.DOWN
        );
        builder.idle(30);
        builder.overlay().showText(70).text("Data provided to the input is sent to all connected outputs.");
        builder.idle(20);
        builder.overlay().showControls(util.vector().topOf(4, 1, 2), Pointing.DOWN, 20).rightClick().withItem(Items.WRITABLE_BOOK.getDefaultInstance());
        builder.idle(10);
        builder.world().setBlock(new BlockPos(4, 1, 2), ModBlocks.DATA_LECTERN.get().defaultBlockState().setValue(DataLecternBlock.HAS_BOOK, true), false);

        String poem = getEndPoem();

        builder.world().modifyBlockEntity(new BlockPos(1, 1, 3), TextDisplayBlockEntity.class, it -> it.acceptText(Channels.MAIN, poem));
        builder.world().modifyBlockEntity(new BlockPos(2, 1, 3), TextDisplayBlockEntity.class, it -> it.acceptText(Channels.MAIN, poem));
        builder.world().modifyBlockEntity(new BlockPos(1, 2, 3), TextDisplayBlockEntity.class, it -> it.acceptText(Channels.MAIN, poem));
        builder.world().modifyBlockEntity(new BlockPos(2, 2, 3), TextDisplayBlockEntity.class, it -> it.acceptText(Channels.MAIN, poem));
        builder.idle(45);
    }

    public static void scriptTerminalTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 5);
        builder.title("script_terminal", "Script Terminal");

        builder.world().showSection(PonderExtras.selectBlocks(builder, util,
                ModBlocks.SCRIPT_TERMINAL.get(),
                ModBlocks.DATA_CABLE.get(),
                ModBlocks.SERIAL_BUS.get(),
                Blocks.WHITE_CONCRETE,
                Blocks.SNOW_BLOCK
        ), Direction.UP);

        builder.idle(10);

        builder.world().showSection(util.select().fromTo(1,2,3,3,2,3), Direction.DOWN);

        builder.idle(10);

        builder.overlay().showText(85).text("The Script Terminal sends commands to interact with other blocks, through connected Serial Busses.");

        builder.idle(95);

        builder.overlay().showText(65).text("To input commands, interact with the Script terminal to open a GUI.");

        builder.idle(75);

        builder.addKeyframe();

        InputWindowElement inputWindowElement = new InputWindowElement(util.vector().topOf(3, 1, 1), Pointing.DOWN);
        builder.addInstruction(new ShowInputInstruction(inputWindowElement, 20));
        inputWindowElement.builder().rightClick();

        builder.idle(12);
        builder.addInstruction(ponderScene -> inputWindowElement.setVisible(false));
        ScreenPonderElement screenElement = new ScreenPonderElement(() -> new ScriptTerminalScreen(null, true));
        builder.addInstruction(new ShowScreenInstruction(screenElement, 144));
        builder.idle(20);
        for (char c : "if block == minecraft:piston[facing=up] set_redstone 15\nset_redstone 0".toCharArray()) {
            typeChar(builder, screenElement, c);
        }
        builder.idle(30);

        ScreenSpaceInputWindowElement element = new ScreenSpaceInputWindowElement(screenElement, (w, h) -> new Vec2(w / 2f - 78, h / 4f + 144), Pointing.UP);
        element.builder().leftClick();
        builder.addInstruction(new ShowScreenRelativeInputInstruction(screenElement, element, 20));
        builder.idle(4);
        builder.addInstruction(new SetScreenMouseInstruction(screenElement, (w, h) -> new Vec2(w / 2f - 78, h / 4f + 142)));
        builder.idle(8);

        builder.idle(5);

        builder.addKeyframe();

        builder.idle(5);

        builder.overlay().showText(70).text("When given a Redstone signal, the Script Terminal will dispatch each of its commands, in sequence.");

        builder.idle(80);

        builder.world().showSection(util.select().position(2,1,1), Direction.DOWN);

        builder.idle(15);

        builder.world().toggleRedstonePower(util.select().position(2,1,1));

        builder.addInstruction(scene -> {
            BlockPos piston = new BlockPos(3,2,3);
            BlockState state = scene.getWorld().getBlockState(piston);
            Direction dir = state.getValue(BlockStateProperties.FACING);

            scene.getWorld().setBlock(piston, state.cycle(PistonBaseBlock.EXTENDED), 0);

            BlockPos headPos = piston.relative(dir);

            scene.getWorld().setBlock(headPos, Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, dir), 0);
        });

        builder.addInstruction(new DisplayWorldSectionInstruction(2, Direction.UP, util.select().position(3,3,3), builder.getScene()::getBaseWorldSection));

        builder.idle(4);

        builder.addInstruction(new FadeOutOfSceneInstruction<>(2, Direction.DOWN, builder.world().makeSectionIndependent(util.select().position(3,3,3))));
        builder.idle(1);
        builder.world().cycleBlockProperty(new BlockPos(3,2,3), PistonBaseBlock.EXTENDED);

        builder.idle(10);

        builder.addKeyframe();

        builder.idle(5);

        builder.overlay().showText(70).text("Each command is executed once for each connected Serial Bus, positioned in front of the Serial Bus interface.");

        builder.idle(80);

        builder.world().toggleRedstonePower(util.select().position(2,1,1));

        builder.idle(10);
        builder.world().toggleRedstonePower(util.select().position(2,1,1));
        builder.idle(10);

        builder.overlay().showText(40).text("if block == minecraft:piston[facing=up] set_redstone 15").colored(PonderPalette.INPUT);


        builder.overlay().showOutline(PonderPalette.GREEN, "a", util.select().position(3 ,2, 3), 35);

        builder.overlay().showOutline(PonderPalette.RED, "b", util.select().position(1 ,2, 3), 35);

        builder.idle(40);

        builder.world().cycleBlockProperty(new BlockPos(3,2,3), PistonBaseBlock.EXTENDED);

        builder.addInstruction(new DisplayWorldSectionInstruction(2, Direction.UP, util.select().position(3,3,3), builder.getScene()::getBaseWorldSection));

        builder.idle(4);

        builder.overlay().showText(40).text("set_redstone 0").colored(PonderPalette.INPUT);

        builder.overlay().showOutline(PonderPalette.GREEN, "a", util.select().position(3 ,2, 3), 35);

        builder.overlay().showOutline(PonderPalette.GREEN, "b", util.select().position(1 ,2, 3), 35);

        builder.idle(40);

        builder.addInstruction(new FadeOutOfSceneInstruction<>(2, Direction.DOWN, builder.world().makeSectionIndependent(util.select().position(3,3,3))));
        builder.idle(1);
        builder.world().cycleBlockProperty(new BlockPos(3,2,3), PistonBaseBlock.EXTENDED);
    }

    // --- Robotic Arm scene helpers ---

    private static long ponderGameTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    /** Snaps the hand to a settled pose with no animation (also used to set the initial rest pose). */
    private static void setArmHand(SceneBuilder builder, SceneBuildingUtil util, BlockPos armPos, BlockPos hand) {
        builder.world().modifyBlockEntityNBT(util.select().position(armPos), RoboticArmBlockEntity.class, nbt -> {
            nbt.putBoolean("Moving", false);
            nbt.putLong("HandBlockPos", hand.asLong());
            nbt.putLong("MoveStartBlockPos", hand.asLong());
            nbt.putLong("MoveTargetBlockPos", hand.asLong());
            nbt.putLong("MoveStartTick", 0L);
        });
    }

    /**
     * Plays a real {@code MOVE_TIME_TICKS}-long hand move by driving the renderer's NBT fields.
     * {@code MoveStartTick} is re-based every scene tick because the client game time is frozen
     * while the Ponder UI is open in singleplayer (the integrated server is paused). Consumes
     * {@code MOVE_TIME_TICKS} scene ticks.
     */
    private static void armMove(SceneBuilder builder, SceneBuildingUtil util, BlockPos armPos, BlockPos from, BlockPos to) {
        Selection arm = util.select().position(armPos);
        for (int i = 0; i < RoboticArmBlockEntity.MOVE_TIME_TICKS; i++) {
            final long tick = i;
            builder.world().modifyBlockEntityNBT(arm, RoboticArmBlockEntity.class, nbt -> {
                nbt.putBoolean("Moving", true);
                nbt.putLong("MoveStartBlockPos", from.asLong());
                nbt.putLong("MoveTargetBlockPos", to.asLong());
                nbt.putLong("MoveStartTick", ponderGameTime() - tick);
            });
            builder.idle(1);
        }
        setArmHand(builder, util, armPos, to);
    }

    private static void setArmHeldStack(SceneBuilder builder, SceneBuildingUtil util, BlockPos armPos, ItemStack stack) {
        builder.world().modifyBlockEntityNBT(util.select().position(armPos), RoboticArmBlockEntity.class, nbt -> {
            if (stack.isEmpty()) {
                nbt.remove("HeldStack"); // absence == empty in readInventoryState
            } else if (Minecraft.getInstance().level != null) {
                nbt.put("HeldStack", stack.save(Minecraft.getInstance().level.registryAccess(), new CompoundTag()));
            }
        });
    }

    public static void roboticArmTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        BlockPos arm = new BlockPos(3, 1, 3);
        BlockPos rest = arm.above();
        BlockPos barrel = new BlockPos(1, 3, 5);
        BlockPos cauldron_0 = new BlockPos(0, 1, 2);
        BlockPos cauldron_1 = new BlockPos(6, 1, 4);
        BlockPos drop = new BlockPos(0, 2, 2); // above cauldron_0

        Selection energy_blocks = util.select().fromTo(4, 1, 1, 4, 1, 3);
        Selection control_blocks = util.select().fromTo(1, 1, 1, 3, 1, 2);
        Selection target_blocks = PonderExtras.selectBlocks(builder, util, Blocks.BARREL, Blocks.CAULDRON, Blocks.LAVA_CAULDRON, ModBlocks.SPACE_TRUSS.get());

        builder.configureBasePlate(0, 0, 7);
        builder.title("robotic_arm", "Robotic Arm");
        builder.showBasePlate();
        setArmHand(builder, util, arm, rest); // rest pose before the arm becomes visible
        builder.idle(5);

        builder.world().showSection(util.select().position(arm), Direction.DOWN);
        builder.idle(10);
        builder.overlay().showText(75)
                .text("The Robotic Arm can interact with and move items between nearby blocks.").placeNearTarget();
        builder.idle(85);

        builder.world().showSection(energy_blocks, Direction.DOWN);
        builder.idle(10);
        builder.overlay().showText(75)
                .text("While active, the arm consumes 8 FE per tick.").placeNearTarget();
        builder.idle(85);

        builder.world().showSection(control_blocks, Direction.DOWN);
        builder.idle(10);
        builder.overlay().showText(65)
                .text("To control the Robotic Arm, send instructions from an adjacent Serial Bus.").placeNearTarget();
        builder.idle(75);
        builder.overlay().showText(65)
                .text("The arm can accept a new instruction once every 16 gameticks.").placeNearTarget();
        builder.idle(75);


        builder.world().showSection(target_blocks, Direction.DOWN);

        builder.idle(10);
        builder.addKeyframe();
        builder.idle(10);

        builder.world().toggleRedstonePower(util.select().position(1,1,1));

        builder.idle(15);

        builder.overlay().showText(30).text("take_items 1 3 5").colored(PonderPalette.INPUT).pointAt(barrel.getCenter()).placeNearTarget();

        armMove(builder, util, arm, rest, barrel);
        builder.idle(15);
        builder.world().setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.OPEN, true), false);
        builder.idle(5);
        builder.world().setBlock(barrel, Blocks.BARREL.defaultBlockState(), false);
        builder.idle(5);

        setArmHeldStack(builder, util, arm, new ItemStack(Items.DIRT)); // barrel handed the arm a dirt block
        builder.idle(10);

        // drop_items 0 2 2 (drop the dirt above cauldron 0, it falls into the lava)
        builder.overlay().showText(30).text("drop_items 0 2 2").colored(PonderPalette.INPUT).pointAt(drop.getCenter()).placeNearTarget();
        armMove(builder, util, arm, barrel, drop);
        builder.idle(10);
        setArmHeldStack(builder, util, arm, ItemStack.EMPTY);
        ElementLink<EntityElement> dirtItem = builder.world()
                .createItemEntity(new Vec3(0.5, 2.2, 2.5), new Vec3(0, -0.1, 0), new ItemStack(Items.DIRT));
        builder.idle(20);
        builder.world().modifyEntity(dirtItem, Entity::discard); // consumed by the lava cauldron
        builder.effects().emitParticles(new Vec3(0.5, 1.6, 2.5),
                builder.effects().simpleParticleEmitter(ParticleTypes.LAVA, new Vec3(0, 0, 0)), 3, 1);
        builder.idle(5);

        // take_items 1 3 5 (retrieve an empty bucket from the barrel)
        builder.overlay().showText(30).text("take_items 1 3 5").colored(PonderPalette.INPUT).pointAt(barrel.getCenter()).placeNearTarget();
        armMove(builder, util, arm, drop, barrel);
        builder.idle(15);
        builder.world().setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.OPEN, true), false);
        builder.idle(5);
        builder.world().setBlock(barrel, Blocks.BARREL.defaultBlockState(), false);
        builder.idle(10);
        setArmHeldStack(builder, util, arm, new ItemStack(Items.BUCKET));
        builder.idle(5);

        // use 0 1 2 (empty lava from cauldron 0 into the held bucket)
        builder.overlay().showText(30).text("use 0 1 2").colored(PonderPalette.INPUT).pointAt(cauldron_0.getCenter()).placeNearTarget();
        armMove(builder, util, arm, barrel, cauldron_0);
        builder.idle(10);
        builder.world().setBlock(cauldron_0, Blocks.CAULDRON.defaultBlockState(), false);
        setArmHeldStack(builder, util, arm, new ItemStack(Items.LAVA_BUCKET));
        builder.effects().emitParticles(new Vec3(0.5, 1.6, 2.5),
                builder.effects().simpleParticleEmitter(ParticleTypes.LAVA, new Vec3(0, 0, 0)), 3, 1);
        builder.idle(25);

        // use 6 1 4 (fill cauldron 1 with lava from the held bucket)
        builder.overlay().showText(30).text("use 6 1 4").colored(PonderPalette.INPUT).pointAt(cauldron_1.getCenter()).placeNearTarget();
        armMove(builder, util, arm, cauldron_0, cauldron_1);
        builder.idle(10);
        builder.world().setBlock(cauldron_1, Blocks.LAVA_CAULDRON.defaultBlockState(), false);
        setArmHeldStack(builder, util, arm, new ItemStack(Items.BUCKET));
        builder.effects().emitParticles(new Vec3(6.5, 1.6, 4.5),
                builder.effects().simpleParticleEmitter(ParticleTypes.LAVA, new Vec3(0, 0, 0)), 3, 1);
        builder.idle(5);

        armMove(builder, util, arm, cauldron_1, rest); // return to the rest pose
        builder.idle(20);
    }



    private static void typeChar(SceneBuilder builder, ScreenPonderElement screenElement, char c) {
        builder.addInstruction(new ModifyScreenInstruction(screenElement, it -> it.charTyped(c, 0)));

        if (c == ']' || c == '\n') {
            builder.idle(4);
        } else if (c == ' ') {
            builder.idle(3);
        } else if (c == '_') {
            builder.idle(2);
        } else {
            builder.idle(1);
        }
    }

    private static @NotNull String getEndPoem() {
        Player player = Minecraft.getInstance().player;
        String playerName = "Player";
        if (player != null) {
            playerName = player.getName().getString();
        }

        return """
                I see the player you mean.
                
                %1$s?
                
                Yes. Take care. It has reached a higher level now. It can read our thoughts.
                
                That doesn’t matter. It thinks we are part of the game.
                
                I like this player. It played well. It did not give up.
                
                It is reading our thoughts as though they were words on a screen.
                
                That is how it chooses to imagine many things, when it is deep in the dream of a game.
                
                Words make a wonderful interface. Very flexible. And less terrifying than staring at the reality behind the screen.
                
                They used to hear voices. Before players could read. Back in the days when those who did not play called the players witches, and warlocks. And players dreamed they flew through the air, on sticks powered by demons.
                
                What did this player dream?
                
                This player dreamed of sunlight and trees. Of fire and water. It dreamed it created. And it dreamed it destroyed. It dreamed it hunted, and was hunted. It dreamed of shelter.
                
                Hah, the original interface. A million years old, and it still works.
                
                But what true structure did this player create, in the reality behind the screen?""".formatted(playerName);
    }

    public static void dataTranscriberTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 5);
        builder.title("data_transcriber", "Data Transcriber");

        builder.world().showSection(PonderExtras.selectBlocks(builder, util,
                ModBlocks.DATA_CABLE.get(),
                ModBlocks.DATA_LECTERN.get(),
                ModBlocks.DATA_TRANSCRIBER.get(),
                Blocks.LECTERN,
                Blocks.WHITE_CONCRETE,
                Blocks.SNOW_BLOCK
        ), Direction.UP);

        builder.idle(10);

        builder.overlay().showText(80).text("The Data Transcriber can write Data to a Book and Quill held in a Lectern or Data Lectern underneath.");

        builder.idle(90);

        builder.overlay().showText(60).text("When powered, it will copy the input Data to the current page of the book below.");

        builder.idle(70);

        builder.world().showSection(util.select().position(2,2,1), Direction.DOWN);
        builder.idle(10);
        builder.world().toggleRedstonePower(util.select().position(2,2,1));

        builder.idle(10);

        builder.overlay().showText(95).text("If the input Data changes while the Data Transcriber is powered, it will turn to the next page and then write the new Data.");

        builder.idle(105);
    }

    /**
     * The reactor's introduction, to the script in design_docs/REACTOR_PONDER_SCRIPTS.md. The
     * structure is a working loop: four Heat Exchangers on top, two under Stepdown Transformers to
     * light it and two under Stepup Transformers to draw power off, with the cable running over the
     * top and down to the Vaporizer that makes the Flux. Nothing in a ponder
     * level runs any of it, so the glow's heat and every flow shown is set by hand here.
     */
    public static void reactorIntroTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        builder.configureBasePlate(0, 0, 9);
        builder.title("reactor_intro", "Setting up a Fusion Reactor");
        builder.scaleSceneView(0.7f);
        builder.setSceneOffsetY(-1.5f);

        BlockPos inputPort = new BlockPos(5, 3, 3);
        BlockPos outputPort = new BlockPos(3, 3, 5);
        BlockPos vent = new BlockPos(1, 5, 5);
        BlockPos[] exchangers = {new BlockPos(5, 5, 4), new BlockPos(4, 5, 5), new BlockPos(6, 5, 5), new BlockPos(5, 5, 6)};
        // One under a Stepdown Transformer and one under a Stepup Transformer, both on the camera's side.
        BlockPos heatingExchanger = new BlockPos(4, 5, 5);
        BlockPos generatingExchanger = new BlockPos(5, 5, 4);

        Selection everything = util.select().fromTo(0, 1, 0, 8, 8, 8);
        Selection cavity = util.select().fromTo(4, 2, 4, 6, 4, 6);
        Selection shell = util.select().fromTo(3, 1, 3, 7, 5, 7).substract(cavity);
        Selection floor = util.select().fromTo(3, 1, 3, 7, 1, 7);
        // The two faces away from the camera are all plating; the two toward it hold the windows.
        Selection backWalls = util.select().fromTo(7, 2, 3, 7, 4, 7).add(util.select().fromTo(3, 2, 7, 6, 4, 7));
        Selection roof = util.select().fromTo(3, 5, 3, 7, 5, 7);
        Selection northFace = util.select().fromTo(3, 2, 3, 6, 4, 3);
        Selection westFace = util.select().fromTo(3, 2, 4, 3, 4, 6);
        Selection ports = util.select().position(inputPort).add(util.select().position(outputPort));
        Selection exchangerBlocks = util.select().fromTo(4, 5, 4, 6, 5, 6)
                .substract(util.select().position(4, 5, 4)).substract(util.select().position(6, 5, 4))
                .substract(util.select().position(4, 5, 6)).substract(util.select().position(6, 5, 6))
                .substract(util.select().position(5, 5, 5));
        Selection fuelLine = util.select().fromTo(5, 1, 1, 5, 3, 1).add(util.select().position(5, 3, 2));
        Selection exhaustLine = util.select().fromTo(1, 3, 5, 2, 3, 5).add(util.select().fromTo(1, 4, 5, 1, 5, 5));
        Selection transformers = util.select().fromTo(4, 6, 4, 6, 6, 6);
        // Two of the exchangers are fed through Stepdown Transformers, to light the reactor; the
        // other two sit under Stepup Transformers, which draw off what it generates.
        Selection stepdowns = util.select().position(4, 6, 5).add(util.select().position(5, 6, 6));
        Selection stepups = util.select().position(5, 6, 4).add(util.select().position(6, 6, 5));
        Selection powerLine = util.select().fromTo(5, 7, 5, 8, 7, 5).add(util.select().fromTo(8, 1, 5, 8, 6, 5))
                .add(util.select().fromTo(8, 1, 1, 8, 1, 4)).add(util.select().fromTo(6, 1, 1, 7, 1, 1));

        SceneViewElement view = new SceneViewElement();
        ReactorGlowElement glow = new ReactorGlowElement(new BlockPos(4, 2, 4), new BlockPos(6, 4, 6), 0.37f);

        // 1. Purpose: the finished reactor, running.
        builder.showBasePlate();
        builder.idle(5);
        builder.world().showSection(everything, Direction.DOWN);
        builder.addInstruction(scene -> {
            scene.addElement(glow);
            glow.heatTo(1.3f, 0);
        });
        builder.idle(25);
        builder.effects().emitParticles(util.vector().topOf(vent),
                builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 1, 80);
        builder.overlay().showText(80)
                .text("The Fusion Reactor produces large amounts of FE by reacting Flux into Aether.");
        builder.idle(95);

        builder.addInstruction(scene -> glow.heatTo(0f, 25));
        builder.idle(25);
        builder.world().hideSection(everything, Direction.UP);
        builder.idle(30);

        // Out of sight, the working blocks give way to stand-ins, so the shell can go up plain.
        builder.world().setBlocks(exchangerBlocks.copy().add(ports),
                ModBlocks.REINFORCED_GLASS.get().defaultBlockState(), false);

        // 2. The shell goes up, the two faces toward the camera left open.
        builder.world().showSection(floor, Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(backWalls, Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(roof, Direction.DOWN);
        builder.idle(10);
        builder.overlay().showText(80).attachKeyFrame()
                .text("A Fusion Reactor is a sealed chamber, built out of Reinforced Plating and Reinforced Glass.");
        builder.idle(95);

        // 3. The inside.
        builder.overlay().showOutline(PonderPalette.WHITE, "cavity", cavity, 70);
        builder.overlay().showText(70).attachKeyFrame()
                .text("The inside of the chamber stays empty, and only holds gas.")
                .pointAt(util.vector().centerOf(5, 3, 5)).placeNearTarget();
        builder.idle(80);

        // 4. Sealing it.
        builder.world().showSection(northFace, Direction.SOUTH);
        builder.idle(10);
        builder.world().showSection(westFace, Direction.EAST);
        builder.idle(25);
        builder.overlay().showOutline(PonderPalette.GREEN, "shell", shell, 45);
        builder.overlay().showText(80)
                .text("Once the chamber is fully sealed, the blocks form a reactor. No controller block is needed.");
        builder.idle(95);

        // 5. The working blocks.
        builder.world().restoreBlocks(ports);
        builder.effects().indicateSuccess(inputPort);
        builder.effects().indicateSuccess(outputPort);
        builder.idle(10);
        builder.overlay().showOutline(PonderPalette.BLUE, "ports", ports, 80);
        builder.overlay().showText(80).attachKeyFrame()
                .text("Reactor Ports move gas through the wall, and can be set to Input or Output.")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(95);

        // Redstone throttles a port: open at no signal, shut at full strength, and in proportion
        // between. The glass beside the Input port gives way to plating for a moment, and a
        // Redstone Converter powers that, which powers the port next to it; a Graduated Lever on
        // the converter sets the level. All of it stands clear of where the fuel duct goes. The
        // port's lamp follows the signal it receives, and is set here as the level is.
        BlockPos poweredWall = inputPort.east();
        BlockPos converterPos = poweredWall.north();
        BlockPos leverPos = converterPos.north();
        Selection throttleRig = util.select().position(converterPos).add(util.select().position(leverPos));
        Selection inputPortOnly = util.select().position(inputPort);

        builder.world().setBlock(poweredWall, ModBlocks.REINFORCED_PLATING.get().defaultBlockState(), true);
        builder.idle(10);
        builder.world().setBlock(converterPos, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, Direction.SOUTH)
                .setValue(CableBlock.NORTH, true), false);
        builder.world().setBlock(leverPos, ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACE, AttachFace.WALL)
                .setValue(PanelBlock.FACING, Direction.NORTH)
                .setValue(PanelBlock.CONNECTED, true), false);
        builder.world().showSection(throttleRig, Direction.SOUTH);
        builder.idle(25);

        builder.overlay().showText(80)
                .text("A Redstone signal will reduce the flow rate through a Reactor Port...")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(15);
        // Up to half, a click at a time.
        for (int level = 1; level <= 8; level++) {
            int power = level;
            builder.world().modifyBlock(leverPos, state -> state.setValue(GraduatedLeverBlock.POWER, power), false);
            builder.world().modifyBlockEntityNBT(inputPortOnly, ReactorPortBlockEntity.class,
                    nbt -> nbt.putInt("Redstone", power));
            builder.idle(5);
        }
        builder.effects().indicateRedstone(poweredWall);
        builder.idle(35);

        builder.overlay().showText(80)
                .text("...and at full strength, it will close the port completely.")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(15);
        for (int level = 9; level <= ReactorPortBlockEntity.MAX_REDSTONE_LEVEL; level++) {
            int power = level;
            builder.world().modifyBlock(leverPos, state -> state.setValue(GraduatedLeverBlock.POWER, power), false);
            builder.world().modifyBlockEntityNBT(inputPortOnly, ReactorPortBlockEntity.class,
                    nbt -> nbt.putInt("Redstone", power));
            builder.idle(5);
        }
        builder.effects().indicateRedstone(poweredWall);
        builder.idle(40);

        // Open again, and the rig away, leaving the window as it was.
        builder.world().modifyBlock(leverPos, state -> state.setValue(GraduatedLeverBlock.POWER, 0), false);
        builder.world().modifyBlockEntityNBT(inputPortOnly, ReactorPortBlockEntity.class, nbt -> nbt.putInt("Redstone", 0));
        builder.idle(15);
        builder.world().hideSection(throttleRig, Direction.NORTH);
        builder.idle(20);
        builder.world().restoreBlocks(throttleRig);
        builder.world().setBlock(poweredWall, ModBlocks.REINFORCED_GLASS.get().defaultBlockState(), true);
        builder.idle(10);

        builder.world().restoreBlocks(exchangerBlocks);
        for (BlockPos exchanger : exchangers) {
            builder.effects().indicateSuccess(exchanger);
        }
        builder.idle(10);
        builder.overlay().showText(80).attachKeyFrame()
                .text("Heat Exchangers convert FE into heat inside the chamber, or heat back into FE.")
                .pointAt(util.vector().topOf(exchangers[0])).placeNearTarget();
        builder.idle(95);

        builder.world().showSection(fuelLine, Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(exhaustLine, Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(transformers, Direction.DOWN);
        builder.idle(5);
        builder.world().showSection(powerLine, Direction.DOWN);
        builder.idle(30);

        // 6. Fuel.
        builder.overlay().showOutline(PonderPalette.INPUT, "fuel", fuelLine.copy().add(util.select().position(inputPort)), 90);
        builder.overlay().showText(90).attachKeyFrame()
                .text("Flux enters through an Input port when the Gas Duct's pressure is higher than the chamber's.")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(100);
        builder.overlay().showText(70)
                .text("Flux has to be heated before it will react.")
                .pointAt(util.vector().centerOf(5, 3, 5)).placeNearTarget();
        builder.idle(80);

        // 7. Ignition: a slow warm-up, then the catch. FE goes in through the Stepdown Transformers.
        // The camera comes round and down to face the north windows dead on, 35 degrees on each
        // axis from where Ponder starts it, and moves in, so the glow is seen straight through the
        // glass and large. Ponder's own rotateCameraY only turns about the one axis.
        //
        // Ponder keeps the middle of the base plate in the middle of the screen, and the reactor
        // is up and to one side of that, so the view slides to centre it. From the north the
        // screen's right is the scene's west: a block to the right and one and a half down.
        Vec3 reactorCentred = new Vec3(-1, -1.5, 0);
        builder.addInstruction(new RotateSceneInstruction(35, 35, true));
        builder.addInstruction(scene -> view.moveTo(scene, 1.5f, reactorCentred, 30));
        builder.idle(30);
        builder.overlay().showOutline(PonderPalette.INPUT, "ignition", stepdowns, 120);
        builder.addInstruction(scene -> glow.heatTo(0.9f, 120));
        builder.overlay().showText(75).attachKeyFrame()
                .text("FE supplied to a Heat Exchanger will heat the gas in the chamber...")
                .pointAt(util.vector().topOf(heatingExchanger)).placeNearTarget();
        builder.idle(130);
        builder.addInstruction(scene -> glow.heatTo(1.3f, 35));
        builder.idle(10);
        // The config default for the ignition temperature, written out because lang text cannot
        // take a value: a pack that moves it wants this line and its lang entry moved too. Each of
        // the three limits is given once, in the scene that is about it. This is this scene's.
        builder.overlay().showText(90)
                .text("...and at 50,000 K, the Flux fuses into Aether, releasing a large amount of heat.")
                .pointAt(util.vector().centerOf(5, 3, 5)).placeNearTarget();
        builder.idle(100);
        builder.addInstruction(new RotateSceneInstruction(-35, -35, true));
        builder.addInstruction(scene -> view.moveTo(scene, 1f, Vec3.ZERO, 30));
        builder.idle(30);

        // 8. Power.
        builder.overlay().showText(70).attachKeyFrame()
                .text("Once ignited, the reaction keeps the chamber hot by itself.");
        builder.idle(80);
        builder.overlay().showOutline(PonderPalette.OUTPUT, "power", stepups, 90);
        builder.overlay().showText(90)
                .text("Heat Exchangers that are not being supplied with FE will generate FE from the chamber's heat.")
                .pointAt(util.vector().topOf(generatingExchanger)).placeNearTarget();
        builder.idle(100);

        // 9. Aether: the reaction sags until the Output port clears it.
        builder.addInstruction(scene -> {
            glow.heatTo(1.05f, 45);
            glow.setFlicker(0.08f);
        });
        builder.overlay().showText(90).attachKeyFrame()
                .text("Aether builds up in the chamber as the Flux reacts. Too much of it will pause the reaction.")
                .pointAt(util.vector().centerOf(5, 3, 5)).placeNearTarget();
        builder.idle(100);
        builder.overlay().showOutline(PonderPalette.OUTPUT, "exhaust", exhaustLine.copy().add(util.select().position(outputPort)), 95);
        builder.effects().emitParticles(util.vector().topOf(vent),
                builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 1, 95);
        builder.addInstruction(scene -> {
            glow.heatTo(1.3f, 60);
            glow.setFlicker(0f);
        });
        builder.overlay().showText(95)
                .text("An Output port can pump the Aether out. Block Flux in its filter, so that the fuel stays inside.")
                .pointAt(util.vector().blockSurface(outputPort, Direction.WEST)).placeNearTarget();
        builder.idle(105);

        // 10. Recap.
        builder.effects().emitParticles(util.vector().topOf(vent),
                builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 1, 110);
        builder.overlay().showText(95).attachKeyFrame()
                .text("The reactor keeps running while Flux is supplied and Aether is removed. If the chamber gets too cold, the reaction will stop.");
        builder.idle(105);
    }

    /**
     * What the three scenes about running a reactor share: the intro's structure, lit, with the
     * monitoring rig on it. The scenes are the script's "tips" in design_docs/
     * REACTOR_PONDER_SCRIPTS.md, one topic each, so that the title says which one a scene is and
     * each starts from a clean set. The three readings are faked along with the glow, and kept in
     * step with it: temperature is heat times ignition temperature, and pressure follows
     * temperature for as long as the contents are the same, as it does in Kelvin.
     */
    private record ReactorRunningSet(BlockPos inputPort, BlockPos outputPort, BlockPos vent, Vec3 chamberCentre,
                                     Selection shell, Selection exchangers, Selection powerLine,
                                     BlockPos[] buses, BlockPos[] displays, Selection busBlocks,
                                     ReactorGlowElement glow) {
    }

    /**
     * Opens one of those scenes: titles it, builds the rig, and brings the whole reactor in running
     * at a steady 65,000 K, with the Vent giving off cloud for a while.
     *
     * @param rigSpace where the scene will later stand a redstone rig, left out of the opening
     *                 section: a block shown twice is drawn twice. Null for none.
     */
    private static ReactorRunningSet openReactorRunningScene(SceneBuilder builder, SceneBuildingUtil util, String id,
                                                             String title, @org.jetbrains.annotations.Nullable Selection rigSpace,
                                                             int cloudTicks) {
        builder.configureBasePlate(0, 0, 9);
        builder.title(id, title);
        builder.scaleSceneView(0.7f);
        builder.setSceneOffsetY(-1.5f);

        BlockPos vent = new BlockPos(1, 5, 5);
        Selection everything = util.select().fromTo(0, 1, 0, 8, 8, 8);
        if (rigSpace != null) {
            everything = everything.substract(rigSpace);
        }
        Selection shell = util.select().fromTo(3, 1, 3, 7, 5, 7).substract(util.select().fromTo(4, 2, 4, 6, 4, 6));
        Selection exchangers = util.select().position(5, 5, 4).add(util.select().position(4, 5, 5))
                .add(util.select().position(6, 5, 5)).add(util.select().position(5, 5, 6));
        Selection powerLine = util.select().position(5, 6, 4).add(util.select().position(6, 6, 5))
                .add(util.select().fromTo(5, 6, 5, 5, 7, 5)).add(util.select().fromTo(6, 7, 5, 8, 7, 5))
                .add(util.select().fromTo(8, 1, 5, 8, 6, 5)).add(util.select().fromTo(8, 1, 1, 8, 1, 4))
                .add(util.select().fromTo(6, 1, 1, 7, 1, 1));

        // The monitoring rig: three chains of Serial Bus, Data Cable and Text Display that never
        // touch. They could not stand side by side: parts on the same cable standard connect
        // wherever they meet, and Text Displays that meet merge into one. So two buses take the
        // ends of the west window's bottom row, a block apart, and the third goes round the corner
        // onto the north window, its cable running back along the plate. All three displays face
        // west on alternate blocks of the plate's edge, where one camera move takes them all in.
        //
        // Window height and not the floor, because a getter only answers from a wall block that
        // touches the cavity, and the floor's edge blocks do not. The redstone rigs that come
        // later stand next to buses, which is fine: they are on a different cable standard.
        BlockPos[] buses = {new BlockPos(4, 2, 2), new BlockPos(2, 2, 4), new BlockPos(2, 2, 6)};
        Direction[] busFacings = {Direction.SOUTH, Direction.EAST, Direction.EAST};
        BlockPos[] displays = {new BlockPos(0, 1, 2), new BlockPos(0, 1, 4), new BlockPos(0, 1, 6)};
        BlockState cableAlong = ModBlocks.DATA_CABLE.get().defaultBlockState()
                .setValue(CableBlock.EAST, true).setValue(CableBlock.WEST, true);
        BlockState cableUp = ModBlocks.DATA_CABLE.get().defaultBlockState()
                .setValue(CableBlock.UP, true).setValue(CableBlock.WEST, true);
        for (int i = 0; i < buses.length; i++) {
            builder.world().setBlock(buses[i], ModBlocks.SERIAL_BUS.get().defaultBlockState()
                    .setValue(TransformerBlock.FACING, busFacings[i]).setValue(CableBlock.DOWN, true), false);
            builder.world().setBlock(buses[i].below(), cableUp, false);
            for (int x = buses[i].getX() - 1; x > displays[i].getX(); x--) {
                builder.world().setBlock(new BlockPos(x, 1, displays[i].getZ()), cableAlong, false);
            }
            builder.world().setBlock(displays[i], ModBlocks.TEXT_DISPLAY.get().defaultBlockState()
                    .setValue(TextDisplayBlock.FACING, Direction.WEST).setValue(TextDisplayBlock.CONNECTED, true), false);
        }
        Selection busBlocks = util.select().position(buses[0]).add(util.select().position(buses[1]))
                .add(util.select().position(buses[2]));

        ReactorGlowElement glow = new ReactorGlowElement(new BlockPos(4, 2, 4), new BlockPos(6, 4, 6), 0.61f);

        builder.showBasePlate();
        builder.idle(5);
        builder.world().showSection(everything, Direction.DOWN);
        builder.addInstruction(scene -> {
            scene.addElement(glow);
            glow.heatTo(1.3f, 0);
        });
        reactorReadings(builder, displays, 9.0e6, 65_000, 8192);
        builder.idle(30);
        if (cloudTicks > 0) {
            builder.effects().emitParticles(util.vector().topOf(vent),
                    builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 1, cloudTicks);
        }
        return new ReactorRunningSet(new BlockPos(5, 3, 3), new BlockPos(3, 3, 5), vent, util.vector().centerOf(5, 3, 5),
                shell, exchangers, powerLine, buses, displays, busBlocks, glow);
    }

    /** Reading the reactor: which values there are, and where a Serial Bus has to point to get them. */
    public static void reactorMonitoringTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        ReactorRunningSet set = openReactorRunningScene(builder, util, "reactor_monitoring",
                "Monitoring a Fusion Reactor", null, 520);
        BlockPos[] buses = set.buses();
        BlockPos[] displays = set.displays();
        Selection busBlocks = set.busBlocks();
        String[] getters = {"reactor_pressure", "reactor_temperature", "reactor_output"};
        SceneViewElement view = new SceneViewElement();

        builder.overlay().showOutline(PonderPalette.INPUT, "buses", busBlocks, 90);
        builder.overlay().showText(90)
                .text("A Serial Bus in Get mode can read the state of a reactor from any of its wall blocks.")
                .pointAt(util.vector().blockSurface(buses[1], Direction.WEST)).placeNearTarget();
        builder.idle(100);
        // One at a time: two of the buses stand either side of the same corner, and their labels
        // would lie over each other.
        for (int i = 0; i < getters.length; i++) {
            builder.overlay().showOutline(PonderPalette.INPUT, "bus", util.select().position(buses[i]), 25);
            builder.overlay().showText(25).text(getters[i]).colored(PonderPalette.INPUT)
                    .pointAt(util.vector().topOf(buses[i])).placeNearTarget();
            builder.idle(30);
        }
        builder.idle(5);
        // The text on a display is a sixteenth of a block tall, far too small at the scene's scale.
        // So for the two lines that are about the values, the camera comes round to face the
        // displays square on, 55 degrees of yaw and 35 of pitch from where Ponder starts it, and
        // moves in a little over three times, with the view raised a block to put the displays
        // mid-screen.
        builder.addInstruction(new RotateSceneInstruction(35, -55, true));
        builder.addInstruction(scene -> view.moveTo(scene, 3.25f, new Vec3(0, 1, 0), 35));
        builder.idle(40);
        builder.overlay().showText(80)
                .text("Pressure is read in Pa, temperature in K, and output in FE per tick.")
                .pointAt(util.vector().blockSurface(displays[1], Direction.WEST)).placeNearTarget();
        builder.idle(95);
        builder.overlay().showText(90)
                .text("Watching these values makes it easier to understand what the reactor is doing, and to avoid a failure.");
        builder.idle(100);
        builder.addInstruction(new RotateSceneInstruction(-35, 55, true));
        builder.addInstruction(scene -> view.moveTo(scene, 1f, Vec3.ZERO, 35));
        builder.idle(40);
    }

    /** Why a reactor overheats, and the two ways to stop it. */
    public static void reactorOverheatingTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        ReactorRunningSet set = openReactorRunningScene(builder, util, "reactor_overheating",
                "Keeping a Fusion Reactor from Overheating", util.select().fromTo(1, 3, 6, 2, 3, 6), 330);
        BlockPos outputPort = set.outputPort();
        BlockPos vent = set.vent();
        Vec3 chamberCentre = set.chamberCentre();
        Selection exchangers = set.exchangers();
        Selection powerLine = set.powerLine();
        BlockPos[] displays = set.displays();
        ReactorGlowElement glow = set.glow();

        // The config default for the melt temperature, written out as in the intro's last line: lang text
        // cannot take a value, so a pack that moves it wants this line and its lang entry moved too.
        builder.overlay().showText(80)
                .text("A Fusion Reactor will melt one of its wall blocks if the chamber exceeds 200,000 K.");
        builder.idle(90);

        // The cause. Same contents, hotter: pressure climbs with the temperature.
        builder.addInstruction(scene -> glow.heatTo(2.2f, 105));
        builder.overlay().showText(95)
                .text("The chamber heats up whenever the reaction generates heat faster than the Heat Exchangers can remove it.")
                .pointAt(chamberCentre).placeNearTarget();
        rampReadings(builder, displays, 9.0e6, 15.2e6, 65_000, 110_000, 8192, 8192, 105);

        // The response: slow the reaction. Hold the Aether in, and it has to wait for it to leave.
        // That takes no heat out by itself. It cuts what is being put in, to below what the Heat
        // Exchangers are taking out, and they are what bring the temperature down.
        BlockPos outputLever = placeThrottleRig(builder, util, outputPort, Direction.SOUTH, Direction.WEST);
        builder.idle(30);
        builder.overlay().showText(95).attachKeyFrame()
                .text("The reaction pauses while Aether is above a quarter of the gas, so the Output port sets how fast it can run.")
                .pointAt(chamberCentre).placeNearTarget();
        builder.idle(105);
        clickThrottle(builder, util, outputLever, outputPort, 0, 10);
        // Less leaving the Vent from here on.
        builder.effects().emitParticles(util.vector().topOf(vent),
                builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 0.3f, 620);
        builder.addInstruction(scene -> glow.heatTo(1.3f, 105));
        builder.overlay().showText(115)
                .text("Restricting the Output port with Redstone keeps more Aether inside, so less heat is generated and the Heat Exchangers can catch up.")
                .pointAt(util.vector().blockSurface(outputPort, Direction.WEST)).placeNearTarget();
        rampReadings(builder, displays, 15.2e6, 9.0e6, 110_000, 65_000, 8192, 8192, 105);
        // The line is a long one: hold on the settled reactor until it has been read.
        builder.idle(20);

        // The exception, where slowing the reaction is not the answer: nowhere for the FE to go.
        // The output falls to nothing and the chamber climbs, until whatever was full is not.
        builder.overlay().showOutline(PonderPalette.RED, "blocked", powerLine, 180);
        builder.addInstruction(scene -> glow.heatTo(1.9f, 190));
        builder.overlay().showText(80).attachKeyFrame()
                .text("A Heat Exchanger can only extract heat while its FE has somewhere to go...")
                .pointAt(util.vector().topOf(5, 6, 4)).placeNearTarget();
        rampReadings(builder, displays, 9.0e6, 11.1e6, 65_000, 80_000, 0, 0, 95);
        builder.overlay().showText(90)
                .text("...so if the machines it powers are full, it will stop cooling the chamber.")
                .pointAt(util.vector().topOf(5, 6, 4)).placeNearTarget();
        rampReadings(builder, displays, 11.1e6, 13.2e6, 80_000, 95_000, 0, 0, 100);
        builder.addInstruction(scene -> glow.heatTo(1.3f, 60));
        rampReadings(builder, displays, 13.2e6, 9.0e6, 95_000, 65_000, 8192, 8192, 60);

        // What more Heat Exchangers are for: not cooling a reactor that runs hot, but letting one
        // run faster.
        builder.overlay().showOutline(PonderPalette.OUTPUT, "exchangers", exchangers, 180);
        builder.overlay().showText(90).attachKeyFrame()
                .text("Each Heat Exchanger can only extract so much heat, so they limit how fast a reactor can safely run.")
                .pointAt(util.vector().topOf(5, 5, 4)).placeNearTarget();
        builder.idle(100);
        builder.overlay().showText(95)
                .text("To produce more FE, add more Heat Exchangers, then open the Output port further. This will also consume more Flux.")
                .pointAt(util.vector().blockSurface(outputPort, Direction.WEST)).placeNearTarget();
        builder.idle(105);
    }

    /** Why a reactor bursts, which is mostly a matter of how it is lit, and how to light it safely. */
    public static void reactorBurstingTutorial(SceneBuilder builder, SceneBuildingUtil util) {
        ReactorRunningSet set = openReactorRunningScene(builder, util, "reactor_bursting",
                "Keeping a Fusion Reactor from Bursting", util.select().fromTo(6, 3, 1, 6, 3, 2), 200);
        BlockPos inputPort = set.inputPort();
        BlockPos vent = set.vent();
        Selection shell = set.shell();
        BlockPos[] displays = set.displays();
        ReactorGlowElement glow = set.glow();

        // The config default for the burst pressure, written out as in the intro's last line: lang text
        // cannot take a value, so a pack that moves it wants this line and its lang entry moved too.
        builder.overlay().showText(80)
                .text("A Fusion Reactor will burst if the pressure in its chamber exceeds 24 MPa.");
        builder.idle(90);

        builder.overlay().showText(80)
                .text("The pressure in the chamber rises and falls with its temperature.");
        builder.idle(95);

        // A cold start with the Input port wide open. 400 kPa of cold gas is nothing next to what
        // the Vaporizer will supply, and is still enough: it is past 24 MPa at 22,500 K, less than
        // half way to ignition.
        builder.addInstruction(scene -> glow.heatTo(0f, 35));
        rampReadings(builder, displays, 9.0e6, 400_000, 65_000, 375, 8192, 0, 35);
        BlockPos inputLever = placeThrottleRig(builder, util, inputPort, Direction.EAST, Direction.NORTH);
        builder.idle(30);
        builder.overlay().showText(100)
                .text("A chamber filled with cold Flux will reach over a hundred times its pressure by the time it ignites...");
        builder.idle(110);
        builder.addInstruction(scene -> glow.heatTo(0.45f, 95));
        rampReadings(builder, displays, 400_000, 24.0e6, 375, 22_500, 0, 0, 95);
        builder.overlay().showOutline(PonderPalette.RED, "burst", shell, 60);
        builder.overlay().showText(80)
                .text("...and will burst long before it gets there.");
        builder.idle(95);

        // Again, with the Input port nearly shut first: 60 kPa cold is 10.4 MPa at running heat.
        builder.addInstruction(scene -> glow.heatTo(0f, 30));
        rampReadings(builder, displays, 24.0e6, 400_000, 22_500, 375, 0, 0, 30);
        clickThrottle(builder, util, inputLever, inputPort, 0, 14);
        reactorReadings(builder, displays, 60_000, 375, 0);
        builder.overlay().showText(95).attachKeyFrame()
                .text("Before igniting, restrict the Input port with Redstone, so that only a little Flux gets in.")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(105);
        builder.addInstruction(scene -> glow.heatTo(1.3f, 140));
        rampReadings(builder, displays, 60_000, 10.4e6, 375, 65_000, 0, 8192, 140);

        builder.effects().emitParticles(util.vector().topOf(vent),
                builder.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.05, 0)), 0.3f, 175);
        builder.overlay().showText(80)
                .text("Once the reactor is lit, the Input port can be opened up again.")
                .pointAt(util.vector().blockSurface(inputPort, Direction.NORTH)).placeNearTarget();
        builder.idle(20);
        clickThrottle(builder, util, inputLever, inputPort, 14, 4);
        rampReadings(builder, displays, 10.4e6, 12.0e6, 65_000, 65_000, 8192, 8192, 25);
        builder.idle(10);

        builder.overlay().showText(95)
                .text("A reactor that overheats gains pressure as well, and can burst before its walls melt.");
        builder.idle(105);
    }

    /** Puts what the three getters would send on the three displays: two decimals for a double, none for an int. */
    private static void reactorReadings(SceneBuilder builder, BlockPos[] displays, double pressurePa, double temperatureK, int outputFe) {
        String[] values = {
                String.format(java.util.Locale.ROOT, "%.2f", pressurePa),
                String.format(java.util.Locale.ROOT, "%.2f", temperatureK),
                Integer.toString(outputFe)
        };
        for (int i = 0; i < displays.length; i++) {
            String value = values[i];
            builder.world().modifyBlockEntity(displays[i], TextDisplayBlockEntity.class, display -> display.acceptText(Channels.MAIN, value));
        }
    }

    /** Moves the readings from one set of values to another over a stretch of ticks, which it idles through. */
    private static void rampReadings(SceneBuilder builder, BlockPos[] displays, double fromPa, double toPa,
                                     double fromK, double toK, int fromFe, int toFe, int ticks) {
        int step = 5;
        for (int elapsed = step; elapsed <= ticks; elapsed += step) {
            float t = (float) elapsed / ticks;
            reactorReadings(builder, displays, fromPa + (toPa - fromPa) * t, fromK + (toK - fromK) * t,
                    Math.round(fromFe + (toFe - fromFe) * t));
            builder.idle(step);
        }
        if (ticks % step != 0) {
            builder.idle(ticks % step);
        }
    }

    /**
     * The redstone rig from the intro, beside a port: the wall block on one side of it becomes
     * plating, a Redstone Converter on that block's outer face powers it, and a Graduated Lever on
     * the converter sets the level. Returns where the lever is.
     *
     * @param along   from the port to the wall block that is powered
     * @param outward the way the port's wall faces
     */
    private static BlockPos placeThrottleRig(SceneBuilder builder, SceneBuildingUtil util, BlockPos port,
                                             Direction along, Direction outward) {
        BlockPos poweredWall = port.relative(along);
        BlockPos converterPos = poweredWall.relative(outward);
        BlockPos leverPos = converterPos.relative(outward);
        builder.world().setBlock(poweredWall, ModBlocks.REINFORCED_PLATING.get().defaultBlockState(), true);
        builder.world().setBlock(converterPos, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, outward.getOpposite())
                .setValue(cableConnection(outward), true), false);
        builder.world().setBlock(leverPos, ModBlocks.GRADUATED_LEVER.get().defaultBlockState()
                .setValue(PanelBlock.FACE, AttachFace.WALL)
                .setValue(PanelBlock.FACING, outward)
                .setValue(PanelBlock.CONNECTED, true), false);
        builder.world().showSection(util.select().position(converterPos).add(util.select().position(leverPos)),
                outward.getOpposite());
        return leverPos;
    }

    private static BooleanProperty cableConnection(Direction direction) {
        return switch (direction) {
            case DOWN -> CableBlock.DOWN;
            case UP -> CableBlock.UP;
            case NORTH -> CableBlock.NORTH;
            case SOUTH -> CableBlock.SOUTH;
            case WEST -> CableBlock.WEST;
            case EAST -> CableBlock.EAST;
        };
    }

    /** Clicks a Graduated Lever a step at a time from one level to another, the port's lamp following, and idles through it. */
    private static void clickThrottle(SceneBuilder builder, SceneBuildingUtil util, BlockPos leverPos, BlockPos port,
                                      int from, int to) {
        int direction = Integer.signum(to - from);
        for (int level = from + direction; direction != 0 && level != to + direction; level += direction) {
            int power = level;
            builder.world().modifyBlock(leverPos, state -> state.setValue(GraduatedLeverBlock.POWER, power), false);
            builder.world().modifyBlockEntityNBT(util.select().position(port), ReactorPortBlockEntity.class,
                    nbt -> nbt.putInt("Redstone", power));
            builder.idle(5);
        }
    }
}
