package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextDisplayBlockEntity extends AbstractTextDataReceiver {

    private static final String COLOR_TAG = "Color";
    private static final String CLEAR_AT_TAG = "ClearAt";

    /** How long a display holds its text after the pipe goes blank, so one-tick sends stay readable. */
    public static final int CLEAR_HOLD_TICKS = 8;

    private DyeColor textColor = DyeColor.WHITE;
    /** The game time a pending clear lands at, or -1 with none pending. */
    private long clearAt = -1;

    public TextDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXT_DISPLAY.get(), pos, state);
    }

    /**
     * New text shows at once; a clear is held back {@link #CLEAR_HOLD_TICKS} ticks, and dropped if
     * text arrives in the meantime. A second clear while one is pending does not push it back.
     */
    @Override
    public void acceptText(int channel, String message) {
        if (!message.isEmpty() || level == null || level.isClientSide) {
            clearAt = -1;
            super.acceptText(channel, message);
            return;
        }
        if (currentDisplayText.isEmpty() || clearAt >= 0) {
            return;
        }
        clearAt = level.getGameTime() + CLEAR_HOLD_TICKS;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), CLEAR_HOLD_TICKS);
    }

    /** From the block's scheduled tick: lands the pending clear once its time has come. */
    public void tickPendingClear() {
        if (clearAt < 0 || level == null) {
            return;
        }
        long remaining = clearAt - level.getGameTime();
        if (remaining > 0) {
            // A tick left over from a clear that was cancelled and then pending again.
            level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) remaining);
            return;
        }
        clearAt = -1;
        super.acceptText(0, "");
    }

    public String getDisplayText() {
        return currentDisplayText;
    }

    public DyeColor getTextColor() {
        return textColor;
    }

    /**
     * Sets the dye colour the text is rendered in, mirroring how a dye is applied
     * to a vanilla sign. Returns {@code false} if the colour was already set.
     */
    public boolean setTextColor(DyeColor color) {
        if (color == textColor) {
            return false;
        }
        textColor = color;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
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
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(COLOR_TAG, textColor.getName());
        if (clearAt >= 0) {
            tag.putLong(CLEAR_AT_TAG, clearAt);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        // Same storage format as vanilla sign text: the dye's registry name.
        textColor = DyeColor.byName(tag.getString(COLOR_TAG), DyeColor.WHITE);
        clearAt = tag.contains(CLEAR_AT_TAG) ? tag.getLong(CLEAR_AT_TAG) : -1;
    }
}
