package g_mungus.zps.client.ponder;

import g_mungus.zps.ZPSMod;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static g_mungus.zps.block.ModBlocks.*;

public class ZPSPonderTags {

    public static final ResourceLocation CAN_INSULATE = ZPSMod.resource("can_insulate");

    public static final ResourceLocation HAS_SCRIPT_CAPS = ZPSMod.resource("has_script_caps");

    @SuppressWarnings("ConstantConditions")
    public static void register(@NotNull PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(CAN_INSULATE)
                .item(CABLE_INSULATION.get())
                .title("Blocks that can be Insulated")
                .description("Components or Blocks which can have Cable Insulation applied to them, which prevents the block from connecting in new directions.")
                .register();

        helper.addToTag(CAN_INSULATE)
                .add(CABLE.getId())
                .add(DENSE_CABLES.getId())
                .add(DATA_CABLE.getId())
                .add(STEPUP_TRANSFORMER.getId())
                .add(STEPDOWN_TRANSFORMER.getId())
                .add(REDSTONE_CONVERTER.getId())
                .add(SERIAL_BUS.getId())
                .add(CABLE_INSULATION.getId());

        helper.registerTag(HAS_SCRIPT_CAPS)
                .item(SCRIPT_TERMINAL.get())
                .title("Blocks with Script Capabilities")
                .description("Blocks which have extra Script Capabilities beyond the default 'set_redstone' command.")
                .register();

        helper.addToTag(HAS_SCRIPT_CAPS)
                .add(SCRIPT_TERMINAL.getId());
                ///  other items are added via a mixin


    }
}
