package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.networking.LoudspeakerTtsPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class LoudspeakerBlockEntity extends AbstractTextDataReceiver {

    public LoudspeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOUDSPEAKER.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        sendTtsPacket(message, getWorldPos());
        if (!message.equals(currentDisplayText)) {
            currentDisplayText = message;
            setChanged();

            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(
                        worldPosition,
                        getBlockState(),
                        getBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        sendTtsPacket("", getWorldPos());
    }

    private void sendTtsPacket(String message, BlockPos worldPos) {
        if (level == null || level.isClientSide) return;
        ZPSGamePackets.INSTANCE.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPos)),
                new LoudspeakerTtsPacket(worldPos, message)
        );
    }

}
