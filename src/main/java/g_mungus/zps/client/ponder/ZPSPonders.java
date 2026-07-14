package g_mungus.zps.client.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import static g_mungus.zps.block.ModBlocks.*;

public class ZPSPonders {

    @SuppressWarnings({"deprecation", "unchecked"})
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<RegistryObject<Block>> HELPER =
                helper.withKeyFunction(block -> block.get().builtInRegistryHolder().key().location());

        HELPER.forComponents(
                CABLE,
                REDSTONE_CONVERTER
        ).addStoryBoard("cable", ZPSPonderScenes::cableTutorial);

        HELPER.forComponents(
                CABLE,
                STEPUP_TRANSFORMER,
                STEPDOWN_TRANSFORMER
        ).addStoryBoard("energy", ZPSPonderScenes::energyTutorial);

        HELPER.forComponents(
                STEPUP_TRANSFORMER,
                REDSTONE_CONVERTER
        ).addStoryBoard("energy_explode", ZPSPonderScenes::energyExplodeTutorial);

        HELPER.forComponents(
                CABLE_INSULATION
        ).addStoryBoard("insulation", ZPSPonderScenes::insulationTutorial, ZPSPonderTags.CAN_INSULATE);

        HELPER.forComponents(
                DENSE_CABLES,
                DENSE_CABLE_SEPARATOR
        ).addStoryBoard("dense_cables", ZPSPonderScenes::denseCablesTutorial);

        HELPER.forComponents(
                OCTO_CONTROLLER
        ).addStoryBoard("octo_controller", ZPSPonderScenes::octoControllerTutorial);

        HELPER.forComponents(
                DATA_CABLE,
                DATA_LECTERN,
                TEXT_DISPLAY
        ).addStoryBoard("data_cable", ZPSPonderScenes::dataCableTutorial);

        HELPER.forComponents(
                SCRIPT_TERMINAL,
                SERIAL_BUS
        ).addStoryBoard("script_terminal", ZPSPonderScenes::scriptTerminalTutorial);

        HELPER.forComponents(
                DATA_TRANSCRIBER
        ).addStoryBoard("data_transcriber", ZPSPonderScenes::dataTranscriberTutorial);

        HELPER.forComponents(
                ROBOTIC_ARM
        ).addStoryBoard("robotic_arm", ZPSPonderScenes::roboticArmTutorial);
    }
}
