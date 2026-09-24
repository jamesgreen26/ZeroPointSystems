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

    private DyeColor textColor = DyeColor.WHITE;

    public TextDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXT_DISPLAY.get(), pos, state);
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
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        // Same storage format as vanilla sign text: the dye's registry name.
        textColor = DyeColor.byName(tag.getString(COLOR_TAG), DyeColor.WHITE);
    }
}
