package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.createmod.catnip.net.packets.ServerboundConfigPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The config screen's packet carries list values, so saving or resetting a list setting does not crash. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigPacketGameTests {

    @GameTest(template = "gametest/flat_11x9x11")
    public static void listConfigValue_roundTrips(GameTestHelper helper) {
        List<String> sent = List.of("vs_clockwork:duct", "other:block");
        ServerboundConfigPacket<List<String>> packet = new ServerboundConfigPacket<>(ZPSMod.MOD_ID, "GasEdgeForeignBlocklist", sent);
        Object received = ServerboundConfigPacket.deserialize(List.of(), packet.serialize(sent));
        helper.assertTrue(sent.equals(received), "A list should come back as sent, came back " + received);
        helper.succeed();
    }
}
