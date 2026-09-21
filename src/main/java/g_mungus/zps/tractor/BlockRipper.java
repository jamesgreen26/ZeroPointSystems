package g_mungus.zps.tractor;

import com.mojang.authlib.GameProfile;
import g_mungus.zps.block.ZPSBrushableBlock;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.entity.TractorCargo;
import g_mungus.zps.mixin.FallingBlockEntityInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pulls blocks loose for a Tractor Beam. Server side only.
 * <p>
 * This is the one machine in the mod that eats terrain at range, so every block goes past the same two checks a
 * player breaking it would: spawn protection and the like through {@code mayInteract}, and a cancellable
 * {@link BlockEvent.BreakEvent} for claim mods.
 */
public final class BlockRipper {
    private static final GameProfile PROFILE =
            new GameProfile(UUID.fromString("5b0f3c1e-7a4d-4e57-9a0b-2c6d1f8e4a73"), "[ZPS Beam Collector]");

    private BlockRipper() {
    }

    public static boolean mayTake(ServerLevel level, BlockPos pos, BlockState state) {
        FakePlayer player = FakePlayerFactory.get(level, PROFILE);
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
    }

    /** A block a piston would break: it breaks where it stands, and the beam pulls in what it drops. */
    public static void destroy(ServerLevel level, BlockPos pos) {
        level.destroyBlock(pos, true);
    }

    /**
     * Turns the block at {@code pos} into a falling block in the same place. The beam then carries it like any
     * other entity, and if the beam lets go it falls and lands as falling blocks do.
     * <p>
     * {@code pos} may be in any grid: the panel's own, the world, or a ship the beam reaches into. The falling
     * block always belongs in the world, so it is put wherever that block appears there.
     *
     * @param carrier the grid the panel rides, if any
     */
    public static FallingBlockEntity pullLoose(ServerLevel level, BeamGeometry beam, @Nullable GridSpace carrier,
                                              BlockPos pos, BlockState state) {
        // Read before the block goes: this is the buried loot of a suspicious block.
        CompoundTag blockData = state.hasBlockEntity() ? ZPSBrushableBlock.snapshot(level, pos) : null;

        BlockState carried = state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                : state;
        // brushCount is not serialised, so a part-brushed block would land with nothing backing its state.
        if (carried.hasProperty(BlockStateProperties.DUSTED)) {
            carried = carried.setValue(BlockStateProperties.DUSTED, 0);
        }

        // Not FallingBlockEntity.fall(), which puts the entity at the block's own coordinates: on a ship those
        // are nowhere near where the block is seen. Resolved from the block's position, not the panel's, since
        // the two need not be in the same grid. An entity's position is the middle of its base, and a falling
        // block is not turned to match a grid, so it is centred on where the block's centre was.
        Vec3 centre = Compat.toWorldPos(level, Vec3.atCenterOf(pos));
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);

        FallingBlockEntity falling = FallingBlockEntityInvoker.zps$create(
                level, centre.x, centre.y - 0.49, centre.z, carried);
        falling.blockData = blockData;
        // Entities tick before block entities, so this block's first tick comes before the beam first moves it.
        // Left at rest it would take a step of gravity there, and a block that was resting on something would
        // land straight back where it came from. Start it off the way the beam will keep it: weightless, and
        // keeping pace with whatever the panel rides.
        Vec3 pull = beam.toWorldVector(level, beam.toLocal(level, centre), beam.pullDirection())
                .scale(BeamForces.PULL_ACCELERATION);
        Vec3 carrierVelocity = carrier == null ? Vec3.ZERO : carrier.velocityAt(centre);
        falling.setDeltaMovement(pull.add(carrierVelocity).add(0.0, falling.getGravity(), 0.0));
        // Its first tick also comes before the beam can say it is carrying it, and one tick of colliding is
        // enough to jam a block that starts out wedged among the ones it was pulled from.
        if (falling instanceof TractorCargo cargo) {
            cargo.zps$carriedByBeam();
        }
        level.addFreshEntity(falling);
        return falling;
    }
}
