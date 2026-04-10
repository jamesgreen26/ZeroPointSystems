package g_mungus.zps.manual;

import g_mungus.zps.client.screens.ManualScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ModManuals {
    public static final List<ManualSection> SCRIPT_COMMANDS_SECTIONS = List.of(
        new ManualSection("home", "index.md", Component.translatable("zps.manual.home")),
        new ManualSection("executors", "executors.md", Component.translatable("zps.manual.executors")),
        new ManualSection("getters", "getters.md", Component.translatable("zps.manual.getters")),
        new ManualSection("mappers", "mappers.md", Component.translatable("zps.manual.mappers")),
        new ManualSection("create", "create.md", Component.translatable("zps.manual.create")),
        new ManualSection("genesis", "genesis.md", Component.translatable("zps.manual.genesis")),
        new ManualSection("valkyrien_skies", "valkyrien_skies.md", Component.translatable("zps.manual.valkyrien_skies"))
    );

    private ModManuals() {
    }

    public static void openScriptCommandsManual(final Level level) {
        if (!level.isClientSide()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ManualScreen(SCRIPT_COMMANDS_SECTIONS, 0));
    }
}
