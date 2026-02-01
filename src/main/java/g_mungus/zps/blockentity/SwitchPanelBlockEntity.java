package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.SwitchPanelBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SwitchPanelBlockEntity extends NetworkTerminalImpl implements RedstoneSendingTerminal{
    public SwitchPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH_PANEL.get(), pos, state);
    }

    @Override
    public int getCurrentSuppliedSignal(int channel) {
        boolean powered = false;
        BlockState state = getBlockState();
        if (state.is(ModBlocks.SWITCH_PANEL.get())) {
            switch (channel) {
                case Channels.QUAD_1 -> powered = state.getValue(SwitchPanelBlock.POWERED_0);
                case Channels.QUAD_2 -> powered = state.getValue(SwitchPanelBlock.POWERED_1);
                case Channels.QUAD_3 -> powered = state.getValue(SwitchPanelBlock.POWERED_2);
                case Channels.QUAD_4 -> powered = state.getValue(SwitchPanelBlock.POWERED_3);
            }
        }

        return powered ? 15 : 0;
    }
}
