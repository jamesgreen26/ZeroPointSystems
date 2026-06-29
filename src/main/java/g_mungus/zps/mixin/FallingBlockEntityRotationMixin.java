package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.joml.Quaternionf;

import g_mungus.zps.contraption.ContraptionRotatedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;

/**
 * Adds a synced + persisted contraption rotation (a full {@link Quaternionf}) to
 * {@link FallingBlockEntity}, so a block detached from a rotating contraption keeps the
 * structure's (possibly nested/composed) orientation while it falls. See
 * {@link ContraptionRotatedEntity}.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityRotationMixin extends Entity implements ContraptionRotatedEntity {

	@Unique
	private static final EntityDataAccessor<Quaternionf> ZPS_ROTATION =
		SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.QUATERNION);

	private FallingBlockEntityRotationMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void zps$defineRotation(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(ZPS_ROTATION, new Quaternionf());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void zps$saveRotation(CompoundTag tag, CallbackInfo ci) {
		Quaternionf q = this.entityData.get(ZPS_ROTATION);
		tag.putFloat("ZpsRotationX", q.x);
		tag.putFloat("ZpsRotationY", q.y);
		tag.putFloat("ZpsRotationZ", q.z);
		tag.putFloat("ZpsRotationW", q.w);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void zps$loadRotation(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains("ZpsRotationW"))
			this.entityData.set(ZPS_ROTATION, new Quaternionf(
				tag.getFloat("ZpsRotationX"), tag.getFloat("ZpsRotationY"),
				tag.getFloat("ZpsRotationZ"), tag.getFloat("ZpsRotationW")));
	}

	@Override
	public void zps$setContraptionRotation(Quaternionf rotation) {
		this.entityData.set(ZPS_ROTATION, new Quaternionf(rotation));
	}

	@Override
	public Quaternionf zps$getContraptionRotation() {
		return this.entityData.get(ZPS_ROTATION);
	}
}
