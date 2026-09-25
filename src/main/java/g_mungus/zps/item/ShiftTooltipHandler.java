package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID, value = Dist.CLIENT)
public class ShiftTooltipHandler {

    /**
     * Format arguments for tooltips that quote a config value, keyed by the tooltip's translation
     * key. Read each time the tooltip is drawn, so they follow the server's config.
     */
    private static final Map<String, Supplier<Object[]>> TOOLTIP_ARGS = Map.of(
            "block.zps.coal_burner.zps_tooltip", () -> new Object[]{ZPSConfig.combustionGeneratorFePerTick()}
    );

    public static boolean hasZpsTooltip(Item item) {
        String key = item.getDescriptionId() + ".zps_tooltip";
        return I18n.exists(key);
    }

    @SubscribeEvent
    public static void limitTooltipWidth(RenderTooltipEvent.GatherComponents event) {
        if (hasZpsTooltip(event.getItemStack().getItem())) {
            event.setMaxWidth(200);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {

        Item item = event.getItemStack().getItem();
        String key = item.getDescriptionId() + ".zps_tooltip";

        if (!I18n.exists(key)) return;

        List<Component> tooltip = event.getToolTip();

        List<Component> elements = tooltip.stream().toList();
        tooltip.clear();
        tooltip.add(elements.get(0));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Hold [")
                    .append(Component.literal("Shift")
                            .withStyle(ChatFormatting.WHITE))
                    .append("] for Summary")
                    .withStyle(ChatFormatting.DARK_GRAY));

            tooltip.add(CommonComponents.EMPTY);

            Supplier<Object[]> args = TOOLTIP_ARGS.get(key);
            Component text = args == null ? Component.translatable(key) : Component.translatable(key, args.get());
            for(String line: text.getString().split("\n")) {
                tooltip.add(formatLine(line));
            }
        } else {
            tooltip.add(Component.literal("Hold [")
                    .append(Component.literal("Shift")
                            .withStyle(ChatFormatting.GRAY))
                    .append("] for Summary")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        for (int i = 1; i < elements.size(); i++) {
            tooltip.add(elements.get(i));
        }
    }

    static final int BASE_COLOR = 0x4c99c9;     // normal text
    private static final int HIGHLIGHT_COLOR = 0x79f1a3; // bracketed text

    private static @NotNull MutableComponent formatLine(String line) {
        MutableComponent result = Component.empty();

        StringBuilder current = new StringBuilder();
        boolean inBrackets = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '[') {
                // Flush current text as base color
                if (!current.isEmpty()) {
                    result.append(Component.literal(current.toString())
                            .withStyle(style -> style.withColor(BASE_COLOR)));
                    current.setLength(0);
                }
                inBrackets = true;
            }
            else if (c == ']') {
                // Flush current text as highlight color
                if (!current.isEmpty()) {
                    result.append(Component.literal(current.toString())
                            .withStyle(style -> style.withColor(HIGHLIGHT_COLOR)));
                    current.setLength(0);
                }
                inBrackets = false;
            }
            else {
                current.append(c);
            }
        }

        // Flush any remaining text
        if (!current.isEmpty()) {
            int color = inBrackets ? HIGHLIGHT_COLOR : BASE_COLOR;
            result.append(Component.literal(current.toString())
                    .withStyle(style -> style.withColor(color)));
        }

        return result;
    }
}