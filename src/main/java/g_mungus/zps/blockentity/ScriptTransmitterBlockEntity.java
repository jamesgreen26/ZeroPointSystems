package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ScriptTransmitterBlockEntity extends NetworkTerminal {
    public ScriptTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIPT_TRANSMITTER.get(), pos, state);
    }
}
