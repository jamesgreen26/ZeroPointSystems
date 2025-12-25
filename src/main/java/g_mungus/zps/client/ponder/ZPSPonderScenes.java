package g_mungus.zps.client.ponder;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.RedstoneConverterBlock;
import g_mungus.zps.item.ModItems;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.scene.SelectionUtil;
import net.createmod.ponder.foundation.PonderSceneBuildingUtil;
import net.createmod.ponder.foundation.PonderWorldParticles;
import net.minecraft.client.particle.GlowParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
        BlockState eastWestInsulatedCable = ModBlocks.CABLE.get().defaultBlockState().setValue(CableBlock.EAST, true).setValue(CableBlock.WEST, true).setValue(CableBlock.INSULATED, true);
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

        builder.idle(30);
        ElementLink<EntityElement> entityA = builder.world().createItemEntity(new BlockPos(3, 1, 1).getCenter(), new Vec3(0,0,0), ModItems.CABLE_INSULATION.get().getDefaultInstance());
        builder.idle(5);
        ElementLink<EntityElement> entityB = builder.world().createItemEntity(new BlockPos(3, 1, 3).getCenter(), new Vec3(0,0,0), ModItems.CABLE_INSULATION.get().getDefaultInstance());
        builder.idle(5);
        ElementLink<EntityElement> entityC = builder.world().createItemEntity(new BlockPos(3, 1, 5).getCenter(), new Vec3(0,0,0), ModItems.CABLE_INSULATION.get().getDefaultInstance());

        builder.world().modifyEntity(entityA, Entity::discard);
        builder.world().setBlock(new BlockPos(3, 1, 1), eastWestInsulatedCable, true);
        builder.idle(5);
        builder.world().modifyEntity(entityB, Entity::discard);
        builder.world().setBlock(new BlockPos(3, 1, 3), eastWestInsulatedCable, true);
        builder.idle(5);
        builder.world().modifyEntity(entityC, Entity::discard);
        builder.world().setBlock(new BlockPos(3, 1, 5), eastWestInsulatedCable, true);
        builder.idle(20);
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


    }
}
