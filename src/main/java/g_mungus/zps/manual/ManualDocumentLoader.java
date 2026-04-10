package g_mungus.zps.manual;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.manual.markdown.ManualDocument;
import g_mungus.zps.manual.markdown.ManualMarkdownParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ManualDocumentLoader {
    private static final String DEFAULT_LANGUAGE = "en_us";

    private ManualDocumentLoader() {
    }

    public static ManualDocument load(final Minecraft minecraft, final ManualSection section) {
        final String languageCode = minecraft.getLanguageManager().getSelected();
        final String content = loadText(minecraft, languageCode, section.documentPath());
        return ManualMarkdownParser.parse(content);
    }

    private static String loadText(final Minecraft minecraft, final String languageCode, final String documentPath) {
        final String normalizedLanguage = languageCode.toLowerCase().replace('-', '_');
        final Resource resource = getResource(minecraft, normalizedLanguage, documentPath);
        if (resource != null) {
            return readResource(resource);
        }

        final Resource fallback = getResource(minecraft, DEFAULT_LANGUAGE, documentPath);
        if (fallback != null) {
            return readResource(fallback);
        }

        return "# Missing Document\n\n`%s` could not be loaded.".formatted(documentPath);
    }

    private static Resource getResource(final Minecraft minecraft, final String languageCode, final String documentPath) {
        return minecraft.getResourceManager()
            .getResource(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "doc/" + languageCode + "/" + documentPath))
            .orElse(null);
    }

    private static String readResource(final Resource resource) {
        try (InputStream stream = resource.open()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "# Document Error\n\n" + exception.getMessage();
        }
    }
}
