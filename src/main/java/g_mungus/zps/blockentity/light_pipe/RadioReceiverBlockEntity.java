package g_mungus.zps.blockentity.light_pipe;

import ace.actually.radios.RadioSignal;
import ace.actually.radios.RadioSpec;
import com.google.common.collect.Multimap;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2ic;
import org.joml.Vector3ic;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RadioReceiverBlockEntity extends NetworkTerminalImpl implements LightPipeDataSender, HasFrequency {

    private int radioFrequencyIndex = 0;
    private String currentDisplayText = "";
    private static final int tickFrequency = 8;
    private final int tickOffset = new Random().nextInt(tickFrequency);

    public void tick() {
        if (level instanceof ServerLevel serverLevel && (serverLevel.getGameTime() + tickOffset) % tickFrequency == 0) {
            List<RadioSignal> signals = RadioSpec.receive(serverLevel, getWorldPos(), getRadioFrequency(), false, List.of());
            String displayText = computeDisplayText(signals);
            if (Objects.equals(currentDisplayText, displayText)) return;
            currentDisplayText = displayText;
            updateClient();
            updateSignal(serverLevel);
        }
    }

    private String computeDisplayText(List<RadioSignal> signals) {
        int length = signals.stream()
                .filter(s -> s.strength() > 0)
                .mapToInt(s -> s.message().length())
                .max()
                .orElse(0);

        if (length == 0) return "";

        List<char[]> messages = signals.stream()
                .map(s -> s.message().toCharArray())
                .toList();

        double[] strengths = signals.stream()
                .mapToDouble(RadioSignal::strength)
                .toArray();

        StringBuilder out = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < length; i++) {

            double totalWeight = 0.0;
            for (int s = 0; s < messages.size(); s++) {
                if (messages.get(s).length > i) {
                    totalWeight += strengths[s];
                }
            }

            if (totalWeight == 0.0) {
                out.append(' ');
                continue;
            }

            double r = random.nextDouble(totalWeight);
            double cumulative = 0.0;

            for (int s = 0; s < messages.size(); s++) {
                char[] msg = messages.get(s);
                if (msg.length <= i) continue;

                cumulative += strengths[s];
                if (r <= cumulative) {
                    out.append(msg[i]);
                    break;
                }
            }
        }

        return out.toString();
    }


    public int getRadioFrequency() {
        return FREQUENCIES.get(radioFrequencyIndex);
    }


    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_RECEIVER.get(), pos, state);
    }

    @Override
    public Vector3ic[][] provideNextVideoFrame(Vector2ic resolution) {
        return new Vector3ic[resolution.x()][resolution.y()];
    }

    @Override
    public String provideNextDisplayText(int length) {
        return currentDisplayText;
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) updateSignal(level);
    }

    @Override
    public void cycleFrequencies() {
        radioFrequencyIndex = (radioFrequencyIndex + 1) % FREQUENCIES.size();
        updateClient();
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentDisplayText = tag.getString("DisplayText");
        radioFrequencyIndex = tag.getInt("RadioFrequencyIndex");
    }
}
