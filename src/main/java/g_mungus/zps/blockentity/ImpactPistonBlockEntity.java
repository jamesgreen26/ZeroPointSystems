package g_mungus.zps.blockentity;

import g_mungus.zps.ModSounds;
import g_mungus.zps.recipe.ImpactInput;
import g_mungus.zps.recipe.ImpactRecipe;
import g_mungus.zps.recipe.ImpactResult;
import g_mungus.zps.recipe.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the Impact Piston's rod and applies {@code zps:impact} recipes to the block beneath it.
 *
 * <p>The stroke is a three-state machine. While powered and fed, the rod winches steadily upward
 * ({@link Phase#RAISING}) over {@link #RAISE_TICKS}; losing power freezes it in place
 * ({@link Phase#HELD}) so a resumed stroke still costs a full {@link #RAISE_TICKS}. Reaching the top commits
 * the machine to a {@link Phase#FALLING} stroke that always completes, whatever happens to the
 * redstone signal in the meantime.
 *
 * <p>Only the four animation fields plus stored energy are synced, and only on phase transitions,
 * so a running piston sends roughly two packets per stroke rather than one per tick. Both render
 * paths reconstruct the rod position from those fields via {@link #rodOffset}.
 */
public class ImpactPistonBlockEntity extends BlockEntity {
    public enum Phase {
        /** Rod parked at {@code phaseStartOffset}: either idle at the bottom, or stalled mid-stroke. */
        HELD,
        RAISING,
        FALLING;

        private static final Phase[] VALUES = values();

        static Phase byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : HELD;
        }
    }

    public static final int ENERGY_CAPACITY = 8192;
    private static final int MAX_RECEIVE = 512;

    /** Ticks of powered operation to winch the rod to the top of its stroke. */
    public static final int RAISE_TICKS = 20;
    /** Ticks the rod takes to fall from the top of its stroke. */
    public static final int FALL_TICKS = 3;
    /** Drawn every tick the rod is being raised. The fall is gravity, and free. */
    public static final int ENERGY_PER_TICK = 64;
    /** Blocks the rod rises above the machine at the top of its stroke. */
    public static final float ROD_TRAVEL = 1.0f;

    private static final int ENERGY_SYNC_INTERVAL_TICKS = 20;
    private static final float IMPACT_VOLUME = 1.0f;
    private static final float IMPACT_PITCH = 0.8f;
    private static final float DRY_DROP_VOLUME = 0.4f;
    private static final float DRY_DROP_PITCH = 0.6f;
    private static final int IMPACT_BLOCK_PARTICLES = 24;
    private static final int IMPACT_CRIT_PARTICLES = 8;

    private final PistonEnergyStorage energyStorage = new PistonEnergyStorage();

    private Phase phase = Phase.HELD;
    private long phaseStartTick;
    private float phaseStartOffset;
    private float phaseDuration;
    /** Server-only: true when the current fall is a work stroke rather than a powered-down retraction. */
    private boolean strike;

    @Nullable
    private ImpactRecipe cachedRecipe;
    @Nullable
    private BlockState cachedRecipeState;

    private int lastSentClientEnergy = -1;
    private long lastEnergySyncTick = Long.MIN_VALUE;

    public ImpactPistonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMPACT_PISTON.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    /**
     * How far the rod sits above its resting position, from 0 (down) to 1 (fully raised). Shared by
     * the Flywheel visual and the BER fallback; the client passes a partial-tick-adjusted elapsed.
     */
    public static float rodOffset(Phase phase, float startOffset, float duration, float elapsed) {
        if (phase == Phase.HELD) {
            return Mth.clamp(startOffset, 0.0f, 1.0f);
        }
        float t = duration <= 0.0f ? 1.0f : Mth.clamp(elapsed / duration, 0.0f, 1.0f);
        return phase == Phase.RAISING
                // A steady winch: linear, so a stroke interrupted and resumed still costs a full raise time.
                ? startOffset + (1.0f - startOffset) * t
                // Gravity: accelerating, so the landing reads as a slam rather than a descent.
                : startOffset * (1.0f - t * t);
    }

    /** Client-side rod position for the current frame. */
    public float getRodOffset(float partialTick) {
        if (level == null) {
            return phaseStartOffset;
        }
        float elapsed = (float) (level.getGameTime() - phaseStartTick) + partialTick;
        return rodOffset(phase, phaseStartOffset, phaseDuration, elapsed);
    }

    public Phase getPhase() {
        return phase;
    }

    private float currentOffset() {
        if (level == null) {
            return phaseStartOffset;
        }
        return rodOffset(phase, phaseStartOffset, phaseDuration, level.getGameTime() - phaseStartTick);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (phase == Phase.FALLING) {
            // The fall is committed once started, and gravity costs nothing.
            if (level.getGameTime() - phaseStartTick >= phaseDuration) {
                land();
            }
            return;
        }

        if (!level.hasNeighborSignal(worldPosition) || getActiveRecipe() == null) {
            float offset = currentOffset();
            if (offset > 0.0f) {
                beginFall(offset, false);
            } else if (phase != Phase.HELD) {
                setPhase(Phase.HELD, 0.0f, 0.0f);
            }
            return;
        }

        if (energyStorage.getEnergyStored() < ENERGY_PER_TICK) {
            // Out of power: freeze the rod where it is so the stroke resumes rather than restarting.
            if (phase != Phase.HELD) {
                setPhase(Phase.HELD, currentOffset(), 0.0f);
            }
            return;
        }
        energyStorage.consume(ENERGY_PER_TICK);
        syncEnergyPeriodically();

        if (phase == Phase.HELD) {
            beginRaise(phaseStartOffset);
        } else if (level.getGameTime() - phaseStartTick >= phaseDuration) {
            beginFall(1.0f, true);
        }
    }

    private void beginRaise(float fromOffset) {
        setPhase(Phase.RAISING, fromOffset, RAISE_TICKS * (1.0f - fromOffset));
    }

    private void beginFall(float fromOffset, boolean strike) {
        this.strike = strike;
        setPhase(Phase.FALLING, fromOffset, FALL_TICKS * fromOffset);
    }

    private void setPhase(Phase phase, float startOffset, float duration) {
        if (level == null) {
            return;
        }
        this.phase = phase;
        this.phaseStartOffset = Mth.clamp(startOffset, 0.0f, 1.0f);
        this.phaseDuration = duration;
        this.phaseStartTick = level.getGameTime();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        lastSentClientEnergy = energyStorage.getEnergyStored();
        lastEnergySyncTick = level.getGameTime();
    }

    /** The rod has landed: fire the effects, and convert the block below if this was a work stroke. */
    private void land() {
        setPhase(Phase.HELD, 0.0f, 0.0f);

        if (!strike) {
            // A rod that fell because the machine lost power still lands, but it lands limply.
            playThunk(DRY_DROP_VOLUME, DRY_DROP_PITCH);
            return;
        }
        strike = false;
        playThunk(IMPACT_VOLUME, IMPACT_PITCH);

        BlockPos below = worldPosition.below();
        BlockState struck = level.getBlockState(below);
        spawnImpactParticles(struck);

        // Re-resolve: the block below may have changed while the rod was in the air.
        ImpactRecipe recipe = resolveRecipe(struck);
        if (recipe == null) {
            return;
        }

        ImpactResult result = recipe.pick(level.random);
        level.setBlockAndUpdate(below, result.block().value().defaultBlockState());
        result.buriedItem().ifPresent(buried -> buryRandomItem(below, buried));
        invalidateRecipeCache();
    }

    private void buryRandomItem(BlockPos pos, Ingredient buried) {
        ItemStack[] candidates = buried.getItems();
        if (candidates.length == 0) {
            return;
        }
        buryItem(level, pos, candidates[level.random.nextInt(candidates.length)].copy());
    }

    /**
     * Stuffs {@code stack} into the block at {@code pos}, if it is brushable. A no-op otherwise.
     *
     * <p>{@code BrushableBlockEntity} keeps its held stack under an {@code item} tag, which it reads
     * back whenever no loot table is set, so this needs no accessor into its private state.
     */
    public static void buryItem(Level level, BlockPos pos, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable)) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.put("item", stack.save(registries));
        brushable.loadWithComponents(tag, registries);
        brushable.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    private void playThunk(float volume, float pitch) {
        level.playSound(null, worldPosition, ModSounds.IMPACT_THUNK.get(), SoundSource.BLOCKS, volume, pitch);
    }

    private void spawnImpactParticles(BlockState struck) {
        if (!(level instanceof ServerLevel serverLevel) || struck.isAir()) {
            return;
        }
        // Debris sprays sideways from the seam between the rod and the block it just hit.
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5;
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, struck),
                x, y, z, IMPACT_BLOCK_PARTICLES, 0.35, 0.05, 0.35, 0.15);
        serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, IMPACT_CRIT_PARTICLES, 0.3, 0.05, 0.3, 0.1);
    }

    @Nullable
    private ImpactRecipe getActiveRecipe() {
        return resolveRecipe(level.getBlockState(worldPosition.below()));
    }

    @Nullable
    private ImpactRecipe resolveRecipe(BlockState state) {
        if (state.isAir()) {
            invalidateRecipeCache();
            return null;
        }
        if (state.equals(cachedRecipeState)) {
            return cachedRecipe;
        }
        cachedRecipeState = state;
        cachedRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipes.IMPACT_TYPE.get(), new ImpactInput(state), level)
                .map(holder -> holder.value())
                .orElse(null);
        return cachedRecipe;
    }

    private void invalidateRecipeCache() {
        cachedRecipe = null;
        cachedRecipeState = null;
    }

    /** Keeps the screen's energy bar roughly current without sending a packet every tick. */
    private void syncEnergyPeriodically() {
        int energy = energyStorage.getEnergyStored();
        if (energy == lastSentClientEnergy) {
            return;
        }
        long gameTime = level.getGameTime();
        if (lastEnergySyncTick != Long.MIN_VALUE && gameTime - lastEnergySyncTick < ENERGY_SYNC_INTERVAL_TICKS) {
            return;
        }
        lastSentClientEnergy = energy;
        lastEnergySyncTick = gameTime;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The rod rises a full block above the machine, so it must not be culled with the base block. */
    public @NotNull AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0, ROD_TRAVEL, 0.0);
    }

    private void writeState(CompoundTag tag) {
        tag.putByte("Phase", (byte) phase.ordinal());
        tag.putLong("PhaseStartTick", phaseStartTick);
        tag.putFloat("PhaseStartOffset", phaseStartOffset);
        tag.putFloat("PhaseDuration", phaseDuration);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    private void readState(CompoundTag tag) {
        phase = Phase.byOrdinal(tag.getByte("Phase"));
        phaseStartTick = tag.getLong("PhaseStartTick");
        phaseStartOffset = tag.getFloat("PhaseStartOffset");
        phaseDuration = tag.getFloat("PhaseDuration");
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag);
        tag.putBoolean("Strike", strike);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        readState(tag);
        strike = tag.getBoolean("Strike");
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeState(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.handleUpdateTag(tag, registries);
        readState(tag);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    private static class PistonEnergyStorage extends EnergyStorage {
        private PistonEnergyStorage() {
            super(ENERGY_CAPACITY, MAX_RECEIVE, 0);
        }

        private void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }

        private void setEnergyStoredExact(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
