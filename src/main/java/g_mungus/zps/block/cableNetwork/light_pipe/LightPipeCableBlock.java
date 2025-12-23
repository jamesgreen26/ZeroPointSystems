package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.core.BuiltinCableStandards;

public class LightPipeCableBlock extends CableBlock {
    public LightPipeCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public String getCableStandard() {
        return BuiltinCableStandards.LIGHT_PIPE;
    }
}
