package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.GraduatedLeverBlock;
import g_mungus.zps.block.cableNetwork.SwitchPanelBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class GraduatedLeverBlockEntity extends NetworkTerminalImpl implements RedstoneSendingTerminal{
    public GraduatedLeverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRADUATED_LEVER.get(), pos, state);
    }

    @Override
    public int getCurrentSuppliedSignal(int channel) {
        int power = 0;
        BlockState state = getBlockState();
        if (channel == Channels.MAIN && state.is(ModBlocks.GRADUATED_LEVER.get())) {
            power = state.getValue(GraduatedLeverBlock.POWER);
        }

        return power;
    }
}
