package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.networking.LoudspeakerTtsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class LoudspeakerBlockEntity extends AbstractTextDataReceiver {

    public LoudspeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOUDSPEAKER.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        if (!message.equals(currentDisplayText)) {
            sendTtsPacket(message, getWorldPos());
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
        PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) level, new ChunkPos(worldPos), new LoudspeakerTtsPacket(worldPos, message));
    }

}
