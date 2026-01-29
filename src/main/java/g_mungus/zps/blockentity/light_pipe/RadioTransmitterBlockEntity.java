package g_mungus.zps.blockentity.light_pipe;

import ace.actually.radios.RadioSpec;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RadioTransmitterBlockEntity extends NetworkTerminalImpl implements LightPipeDataReceiver.Text, RadioBlockEntity {
    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_TRANSMITTER.get(), pos, state);
    }

    private String currentDisplayText = "";
    private int radioFrequencyIndex = 0;
    private int antennas = 0;

    @Override
    public void cycleFrequencies() {
        clearTransmission();
        radioFrequencyIndex = (radioFrequencyIndex + 1) % FREQUENCIES.size();
        setChanged();
        updateTransmission();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    @Override
    public int getRadioFrequency() {
        return FREQUENCIES.get(radioFrequencyIndex);
    }

    public void updateTransmission() {
        if (level instanceof ServerLevel serverLevel) {
            if (antennas >= getRequiredAntennaStrength()) {
                RadioSpec.transmit(serverLevel, getBlockPos(), getRadioFrequency(), currentDisplayText, "");
            } else {
                clearTransmission();
            }
        }
    }

    public void clearTransmission() {
        if (level instanceof ServerLevel serverLevel) {
            RadioSpec.transmit(serverLevel, getBlockPos(), getRadioFrequency(), "", "");
        }
    }

    @Override
    public void acceptText(int channel, String message) {
        if (!message.equals(currentDisplayText)) {
            currentDisplayText = message;
            setChanged();
            updateTransmission();

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
    public void updateAntennaStrength(LevelAccessor level) {
        int up = 0;
        int result = 0;
        do {
            up++;
            if (RadioBlockEntity.isValidAntennaBlock(level.getBlockState(getBlockPos().offset(0, up, 0)))) {
                result++;
            }
        } while (up == result);
        if (result != antennas) {
            antennas = result;
            updateTransmission();
        }
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            if (!hasSender(level)) {
                acceptText(channel, "");
            } else if (level instanceof ServerLevel) {
                updateAntennaStrength(level);
            }
        }
    }

    private boolean hasSender(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null && pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("DisplayText", currentDisplayText);
        tag.putInt("RadioFrequencyIndex", radioFrequencyIndex);
        tag.putInt("AntennaStrength", antennas);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentDisplayText = tag.getString("DisplayText");
        radioFrequencyIndex = tag.getInt("RadioFrequencyIndex");
        antennas = tag.getInt("AntennaStrength");
        if (level instanceof ServerLevel) {
            updateAntennaStrength(level);
        }
    }
}
