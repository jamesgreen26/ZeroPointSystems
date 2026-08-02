package g_mungus.zps.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;

public class SerialBusManualDisplayLinkSource extends SerialBusDisplayLinkSource {
    @Override
    protected String getText(DisplayLinkContext context, SerialBusBlockEntity serialBus) {
        if (context.blockEntity() instanceof DisplayLinkManualTextAccessor accessor) {
            return accessor.zps$getManualDisplayText();
        }
        return "";
    }
}
