package g_mungus.zps.manual;

import g_mungus.zps.client.screens.ManualScreen;
import g_mungus.zps.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class ModManuals {
    public static final List<ManualSection> SCRIPT_COMMANDS_SECTIONS = buildScriptCommandsSections();

    private static List<ManualSection> buildScriptCommandsSections() {
        final List<ManualSection> sections = new ArrayList<>();
        sections.add(new ManualSection("home", "index.md", Component.translatable("zps.manual.home")));
        sections.add(new ManualSection("executors", "executors.md", Component.translatable("zps.manual.executors")));
        sections.add(new ManualSection("getters", "getters.md", Component.translatable("zps.manual.getters")));
        sections.add(new ManualSection("mappers", "mappers.md", Component.translatable("zps.manual.mappers")));
        if (Compat.isCreateLoaded()) {
            sections.add(new ManualSection("create", "create.md", Component.translatable("zps.manual.create")));
        }
        if (Compat.isSableLoaded()) {
            sections.add(new ManualSection("sable", "sable.md", Component.translatable("zps.manual.sable")));
        }
        if (Compat.isVSLoaded()) {
            sections.add(new ManualSection("valkyrien_skies", "valkyrien_skies.md", Component.translatable("zps.manual.valkyrien_skies")));
        }
        return List.copyOf(sections);
    }

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
