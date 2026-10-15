package g_mungus.zps.blockentity;

import g_mungus.zps.entity.ControllerSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * What the Octo- and Dodeca-Controller share: a run of redstone channels driven by whoever is riding
 * the seat in front, and the keys that drive them.
 *
 * <p>The keys belong to the block, not to the player's controls: each controller is bound on its own,
 * from its own screen, and goes to clients with the block so the rider's client knows what to read.
 * They are GLFW key codes, kept as plain ints because the server never loads GLFW.
 */
public abstract class ControllerBlockEntity<T extends ControllerSeatEntity> extends RideableNetworkTerminal<T>
        implements RedstoneSendingTerminal {

    /** GLFW's code for a key that is not bound. */
    public static final int UNBOUND = -1;

    /**
     * W A S D; then up, left, down, right as Space, Q, Left Shift, E; then Z X C V. Shift is free to
     * be a key here because sneaking neither dismounts nor crouches a rider.
     */
    private static final int[] DEFAULT_KEYS = {87, 65, 83, 68, 32, 81, 340, 69, 90, 88, 67, 86};

    private static final String KEYS_TAG = "Keys";

    private final ConcurrentMap<Integer, Integer> outputSignals = new ConcurrentHashMap<>();
    private final int[] keys;

    protected ControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < channelCount(); i++) {
            outputSignals.put(firstChannel() + i, 0);
        }
        keys = defaultKeys(channelCount());
    }

    /** The network channel of the first output; the rest follow it in order. */
    protected abstract int firstChannel();

    public abstract int channelCount();

    public static int[] defaultKeys(int count) {
        return Arrays.copyOf(DEFAULT_KEYS, count);
    }

    @Override
    void registerSeatEntity(T seat, Vec3i offset) {
        seat.blockEntity = this;
        seat.offset = offset;
    }

    @Override
    public int getCurrentSuppliedSignal(int channel) {
        return outputSignals.getOrDefault(channel, 0);
    }

    /** Sets every output from a bitmask of the pressed keys, bit 0 being the first channel. */
    public void setPressed(int mask) {
        for (int i = 0; i < channelCount(); i++) {
            supplySignal(firstChannel() + i, (mask & (1 << i)) != 0 ? 15 : 0);
        }
    }

    private void supplySignal(int channel, int strength) {
        if (outputSignals.get(channel) == strength) return;
        outputSignals.put(channel, strength);
        updateAllSignals(level, getTerminals(channel));
    }

    // --- keys ----------------------------------------------------------------------------------

    public int getKey(int index) {
        return keys[index];
    }

    /** Every key at once, as the screen sends them; anything not one per channel is ignored. */
    public void setKeys(int[] newKeys) {
        if (newKeys.length != keys.length || Arrays.equals(keys, newKeys)) return;
        System.arraycopy(newKeys, 0, keys, 0, keys.length);
        keysChanged();
    }

    private void keysChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void loadKeys(CompoundTag tag) {
        if (!tag.contains(KEYS_TAG)) return;
        int[] saved = tag.getIntArray(KEYS_TAG);
        System.arraycopy(saved, 0, keys, 0, Math.min(saved.length, keys.length));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        loadKeys(tag);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(KEYS_TAG, keys);
    }

    /** Only the keys go to clients: the rider's client reads them, and the screen shows them. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(KEYS_TAG, keys);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        loadKeys(tag);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
