package g_mungus.zps.reactor;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The two ways a reactor breaks. Both take the reactor out of the registry <em>before</em>
 * touching the world, so the wall block's own removal hook finds nothing to dissolve and simply
 * rescans a cavity that is no longer sealed.
 */
public final class ReactorFailures {

    private static final float BREACH_DAMAGE = 6.0f;
    private static final float BREACH_BURN_SECONDS = 8.0f;

    private ReactorFailures() {
    }

    /**
     * Superheated gas escapes through one wall block. The block is destroyed if it is still there,
     * everything around the hole is set alight, and the reactor is gone.
     *
     * @param wall        the wall block that gives way
     * @param destroyWall false when the wall is already gone, e.g. a player just broke it
     */
    public static void breach(ServerLevel level, Reactor reactor, BlockPos wall, boolean destroyWall) {
        ReactorManager.get(level).dissolve(level, reactor);

        if (destroyWall) {
            level.destroyBlock(wall, true);
        }

        int fireRadius = ZPSConfig.breachFireRadius();
        for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-fireRadius, -fireRadius, -fireRadius),
                wall.offset(fireRadius, fireRadius, fireRadius))) {
            if (reactor.isInterior(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockState fire = BaseFireBlock.getState(level, pos);
            if (fire.canSurvive(level, pos)) {
                level.setBlock(pos, fire, Block.UPDATE_ALL);
            }
        }

        int igniteRadius = ZPSConfig.breachIgniteRadius();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(wall).inflate(igniteRadius))) {
            entity.igniteForSeconds(BREACH_BURN_SECONDS);
            entity.hurt(level.damageSources().inFire(), BREACH_DAMAGE);
        }

        Vec3 centre = Vec3.atCenterOf(wall);
        level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 80, 1.0, 1.0, 1.0, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 40, 1.5, 1.5, 1.5, 0.05);
        level.playSound(null, wall, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.6f);

        award(level, reactor, "reactor_meltdown", "melted");
        ZPSMod.LOGGER.info("Reactor {} breached at {}", reactor.id(), wall);
    }

    /** The shell lets go at one wall block, with a blast scaled by how far over the limit it went. */
    public static void burst(ServerLevel level, Reactor reactor, double pressure) {
        ReactorManager.get(level).dissolve(level, reactor);

        BlockPos wall = reactor.randomWall(level.random);
        double overshoot = Math.max(0, pressure / reactor.burstPressure() - 1.0);
        float radius = (float) Mth.clamp(2.0 + ZPSConfig.burstRadiusPerOvershoot() * overshoot, 2.0, 8.0);
        Vec3 centre = Vec3.atCenterOf(wall);
        level.explode(null, centre.x, centre.y, centre.z, radius, Level.ExplosionInteraction.BLOCK);
        ZPSMod.LOGGER.info("Reactor {} burst at {} ({} Pa against {} Pa)", reactor.id(), wall, pressure, reactor.burstPressure());
    }

    /** Hand an advancement to every player close enough to have been part of it. */
    public static void award(ServerLevel level, Reactor reactor, String advancementPath, String criterion) {
        AdvancementHolder advancement = level.getServer().getAdvancements().get(ZPSMod.resource(advancementPath));
        if (advancement == null) {
            return;
        }
        double radius = ZPSConfig.reactorAdvancementRadius();
        Vec3 centre = Vec3.atCenterOf(reactor.host());
        for (ServerPlayer player : level.players()) {
            if (player.position().closerThan(centre, radius)) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }
}
