package g_mungus.zps.client.ponder;

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
}
