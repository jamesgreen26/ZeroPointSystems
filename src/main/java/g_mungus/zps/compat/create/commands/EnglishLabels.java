package g_mungus.zps.compat.create.commands;

import g_mungus.zps.ZPSMod;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Labels in English, whatever the language and on either side. Script command names are made from
 * the labels of Create's settings, and those of any addon's, so they have to read the same
 * everywhere: a dedicated server has no mod translations at all, and a client has the player's
 * language. Every loaded mod's English translations are read once, from its own jar.
 */
final class EnglishLabels {
    private static final String LANG_FILE = "en_us.json";

    private static @Nullable Map<String, String> english;

    private EnglishLabels() {
    }

    /** {@code label} as its mod's English translation has it, or as it reads if that has none. */
    static String of(Component label) {
        if (label.getContents() instanceof TranslatableContents translatable) {
            String text = english().get(translatable.getKey());
            if (text != null) {
                return text;
            }
        }
        return label.getString();
    }

    private static synchronized Map<String, String> english() {
        if (english == null) {
            english = load();
        }
        return english;
    }

    private static Map<String, String> load() {
        Map<String, String> translations = new HashMap<>();
        for (IModFileInfo modFile : ModList.get().getModFiles()) {
            Path assets = modFile.getFile().findResource("assets");
            if (!Files.isDirectory(assets)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(assets)) {
                for (Path namespace : namespaces.toList()) {
                    Path lang = namespace.resolve("lang").resolve(LANG_FILE);
                    if (Files.isRegularFile(lang)) {
                        read(lang, translations);
                    }
                }
            } catch (IOException e) {
                ZPSMod.LOGGER.warn("Could not list the assets of {}", modFile.getFile().getFileName(), e);
            }
        }
        return translations;
    }

    private static void read(Path lang, Map<String, String> translations) {
        try (InputStream in = Files.newInputStream(lang)) {
            Language.loadFromJson(in, translations::put);
        } catch (IOException | RuntimeException e) {
            ZPSMod.LOGGER.warn("Could not read English labels from {}", lang, e);
        }
    }
}
