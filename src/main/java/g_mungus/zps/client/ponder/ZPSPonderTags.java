package g_mungus.zps.client.ponder;

import g_mungus.zps.ZPSMod;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static g_mungus.zps.block.ModBlocks.*;

public class ZPSPonderTags {

    public static final ResourceLocation CAN_INSULATE = ZPSMod.resource("can_insulate");

    public static final ResourceLocation CABLE_COMPONENTS = ZPSMod.resource("cable_components");

    public static final ResourceLocation DATA_CABLE_COMPONENTS = ZPSMod.resource("data_cable_components");

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

        helper.registerTag(CABLE_COMPONENTS)
                .item(CABLE.get())
                .title("Cable Components")
                .description("Blocks that carry or use cable signals.")
                .addToIndex()
                .register();

        helper.addToTag(CABLE_COMPONENTS)
                .add(CABLE.getId())
                .add(DENSE_CABLES.getId())
                .add(DENSE_CABLE_SEPARATOR.getId())
                .add(STEPUP_TRANSFORMER.getId())
                .add(STEPDOWN_TRANSFORMER.getId())
                .add(REDSTONE_CONVERTER.getId())
                .add(OCTO_CONTROLLER.getId())
                .add(DODECA_CONTROLLER.getId())
                .add(SWITCH_PANEL.getId())
                .add(GRADUATED_LEVER.getId());

        helper.registerTag(DATA_CABLE_COMPONENTS)
                .item(DATA_CABLE.get())
                .title("Data Cable Components")
                .description("Blocks that send or receive messages through Data Cables.")
                .addToIndex()
                .register();

        helper.addToTag(DATA_CABLE_COMPONENTS)
                .add(DATA_CABLE.getId())
                .add(DATA_LECTERN.getId())
                .add(TEXT_DISPLAY.getId())
                .add(DATA_TRANSCRIBER.getId())
                .add(RADIO_TRANSMITTER.getId())
                .add(RADIO_RECEIVER.getId())
                .add(DATA_COMPARATOR.getId())
                .add(DATA_COMBINATOR.getId())
                .add(SERIAL_BUS.getId())
                .add(SCRIPT_TERMINAL.getId())
                .add(LOUDSPEAKER.getId());

        helper.registerTag(HAS_SCRIPT_CAPS)
                .item(SCRIPT_TERMINAL.get())
                .title("Blocks with Script Capabilities")
                .description("Blocks which have extra Script Capabilities beyond the default 'set_redstone' command.")
                .addToIndex()
                .register();

        helper.addToTag(HAS_SCRIPT_CAPS)
                .add(SCRIPT_TERMINAL.getId());
                ///  other items are added via a mixin


    }
}
