package g_mungus.zps.blockentity;

import g_mungus.zps.block.LargeSpaceDoorBlock;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/** The large space door's controller: slides the halves open and shut over about a second and a half. */
public class LargeSpaceDoorBlockEntity extends BlockEntity {
    public static final int FULLY_OPEN = 100;
    private static final int STEP = 3;

    private int slide;
    private int lastSlide;

    public LargeSpaceDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_SPACE_DOOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LargeSpaceDoorBlockEntity door) {
        boolean open = LargeSpaceDoorBlock.isOpen(state);
        door.lastSlide = door.slide;
        if (!level.isClientSide()) {
            if (open && door.slide == STEP) {
                level.playSound(null, pos, ModBlocks.SPACE_METAL_SET.doorOpen(), SoundSource.BLOCKS, 1, 0.8f);
            } else if (!open && door.slide == FULLY_OPEN - STEP) {
                level.playSound(null, pos, ModBlocks.SPACE_METAL_SET.doorClose(), SoundSource.BLOCKS, 1, 0.8f);
            }
        }
        int slide = Mth.clamp(door.slide + (open ? STEP : -STEP), 0, FULLY_OPEN);
        // A deep thud as the halves hit their stops.
        if (!level.isClientSide() && slide != door.slide && (slide == 0 || slide == FULLY_OPEN)) {
            level.playSound(null, pos, SoundEvents.HEAVY_CORE_PLACE, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        door.slide = slide;
    }

    /** How far open the door is, from 0 to 1. */
    public float openness(float partialTick) {
        return Mth.lerp(partialTick, lastSlide, slide) / FULLY_OPEN;
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        slide = lastSlide = tag.getInt("Slide");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Slide", slide);
    }

    /** Sent with the chunk, so a door loaded open is drawn open rather than sliding open. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /** The halves slide a little over a block past the doorway on either side. */
    public @NotNull AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3, 0, 3).expandTowards(0, 3, 0);
    }
}
