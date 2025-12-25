package g_mungus.zps.client.ponder;

import g_mungus.zps.block.ModBlocks;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class ZPSPonders {
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<Block> HELPER =
                helper.withKeyFunction(block -> block.builtInRegistryHolder().key().location());

        HELPER.forComponents(
                ModBlocks.CABLE.get(),
                ModBlocks.REDSTONE_CONVERTER.get()
        ).addStoryBoard("cable", ZPSPonderScenes::cableTutorial);

        HELPER.forComponents(
                ModBlocks.CABLE.get(),
                ModBlocks.STEPUP_TRANSFORMER.get(),
                ModBlocks.STEPDOWN_TRANSFORMER.get()
        ).addStoryBoard("energy", ZPSPonderScenes::energyTutorial);

    }
}
