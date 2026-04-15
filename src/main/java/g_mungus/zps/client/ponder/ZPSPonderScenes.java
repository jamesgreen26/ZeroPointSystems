package g_mungus.zps.client.ponder;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.DenseCableSeparatorBlock;
import g_mungus.zps.block.cableNetwork.RedstoneConverterBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.DataLecternBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import g_mungus.zps.client.ponder.api.PonderExtras;
import g_mungus.zps.client.ponder.api.custom_screen_in_ponder_scene.*;
import g_mungus.zps.client.screens.ScriptTerminalScreen;
import g_mungus.zps.item.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.createmod.ponder.foundation.instruction.ShowInputInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        BlockState eastWestInsulatedCable = ModBlocks.CABLE.get().defaultBlockState().setValue(CableBlock.EAST, true).setValue(CableBlock.WEST, true).setValue(CableBlock.INSULATION_TYPE, CableBlock.InsulationType.INSULATION);
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

    @SuppressWarnings("deprecation")
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
            if (state.is(ModBlocks.LIGHT_PIPE.get()) && (
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
                PonderExtras.selectBlocks(builder, util, ModBlocks.LIGHT_PIPE.get()),
                Direction.UP
        );

        builder.idle(5);
        builder.overlay().showText(60).text("Data Cables send information as text between compatible blocks.");
        builder.idle(60);
        builder.overlay().showText(60).text("A minimum setup has an input and output.");
        builder.idle(20);
        builder.world().showSection(util.select().position(4, 1, 2), Direction.DOWN);
        builder.world().setBlock(new BlockPos(4, 1, 3), ModBlocks.LIGHT_PIPE.get().defaultBlockState().setValue(CableBlock.NORTH, true).setValue(CableBlock.SOUTH, true), false);

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
                ModBlocks.LIGHT_PIPE.get(),
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
                ModBlocks.LIGHT_PIPE.get(),
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
}
