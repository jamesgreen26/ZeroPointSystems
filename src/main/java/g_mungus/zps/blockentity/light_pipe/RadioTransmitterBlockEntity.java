package g_mungus.zps.blockentity.light_pipe;

import ace.actually.radios.api.RadioSpec;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
    private int radioFrequency = MIN_FREQUENCY;
    private int antennas = 0;

    @Override
    public void cycleFrequencies() {
        clearTransmission();
        radioFrequency = radioFrequency % MAX_FREQUENCY + 1;
        setChanged();
        updateTransmission();

        updateClient();
    }

    @Override
    public void setFrequency(int frequency) {
        assert frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY;
        clearTransmission();
        radioFrequency = frequency;
        setChanged();
        updateTransmission();

        updateClient();
    }

    @Override
    public int getRadioFrequency() {
        return radioFrequency;
    }

    public void updateTransmission() {
        if (level instanceof ServerLevel serverLevel) {
            if (hasEnoughAntennas()) {
                RadioSpec.transmit(serverLevel, getBlockPos(), getRadioFrequency(), currentDisplayText, "");
            } else {
                clearTransmission();
            }
        }
    }

    @Override
    public boolean hasEnoughAntennas() {
        return antennas >= getRequiredAntennaStrength();
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

            updateClient();
        }
    }

    private void updateClient() {
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
            updateClient();
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

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null && pkt.getTag() != null && level != null) {
            loadAdditional(pkt.getTag(), level.registryAccess());
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("DisplayText", currentDisplayText);
        tag.putInt("RadioFrequency", radioFrequency);
        tag.putInt("AntennaStrength", antennas);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        currentDisplayText = tag.getString("DisplayText");
        if (tag.contains("RadioFrequency")) {
            radioFrequency = tag.getInt("RadioFrequency");
        }
        antennas = tag.getInt("AntennaStrength");
        if (level instanceof ServerLevel) {
            updateAntennaStrength(level);
        }
    }
}
