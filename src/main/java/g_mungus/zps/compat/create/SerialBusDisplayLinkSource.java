package g_mungus.zps.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class SerialBusDisplayLinkSource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof SerialBusBlockEntity serialBus))
            return List.of();

        int maxRows = stats.maxRows();
        int maxCols = stats.maxColumns();

        if (maxRows <= 0 || maxCols <= 0)
            return List.of();

        String text = getText(context, serialBus);
        if (text == null || text.isEmpty())
            return List.of();

        List<MutableComponent> result = new ArrayList<>(maxRows);

        String[] rawLines = text.split("\n");

        for (String rawLine : rawLines) {
            if (result.size() >= maxRows)
                break;

            // Hard wrap the line to maxCols
            int index = 0;
            while (index < rawLine.length() && result.size() < maxRows) {
                int end = Math.min(index + maxCols, rawLine.length());
                String segment = rawLine.substring(index, end);
                result.add(Component.literal(segment));
                index = end;
            }

            // Handle empty line case
            if (rawLine.isEmpty()) {
                result.add(Component.literal(""));
            }
        }

        return result;
    }

    @Override
    public boolean shouldPassiveReset() {
        return false;
    }

    protected String getText(DisplayLinkContext context, SerialBusBlockEntity serialBus) {
        return serialBus.getCurrentText();
    }
}
