package g_mungus.zps.client.renderer.text;

import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;

/** Every character as itself, in the colour a dyed sign's text takes. */
public final class DefaultTextVisualizer implements TextVisualizer {

    @Override
    public List<VisualGlyph> visualize(String text, DyeColor dye) {
        int color = dye.getTextColor();
        List<VisualGlyph> glyphs = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            glyphs.add(new VisualGlyph(text.charAt(i), color));
        }
        return glyphs;
    }
}
