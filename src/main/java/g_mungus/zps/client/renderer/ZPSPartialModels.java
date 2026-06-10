package g_mungus.zps.client.renderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import g_mungus.zps.ZPSMod;

public final class ZPSPartialModels {
    public static final PartialModel ROBOTIC_ARM_SEGMENT =
            PartialModel.of(ZPSMod.resource("block/robotic_arm_segment"));
    public static final PartialModel ROBOTIC_ARM_SWIVEL_BASE =
            PartialModel.of(ZPSMod.resource("block/robotic_arm_swivel_base"));

    private ZPSPartialModels() {
    }

    /// Forces classloading so the partials exist before Flywheel registers them for baking.
    public static void init() {
    }
}
