package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import g_mungus.zps.contraption.ContraptionRotatedEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;

/**
 * Adds a synced + persisted contraption rotation (angle about one axis) to
 * {@link FallingBlockEntity}, so a block detached from a rotating contraption keeps the
 * structure's orientation while it falls. See {@link ContraptionRotatedEntity}.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityRotationMixin extends Entity implements ContraptionRotatedEntity {

	@Unique
	private static final EntityDataAccessor<Float> ZPS_ROTATION_ANGLE =
		SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.FLOAT);
	@Unique
	private static final EntityDataAccessor<Byte> ZPS_ROTATION_AXIS =
		SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.BYTE);

	private FallingBlockEntityRotationMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void zps$defineRotation(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(ZPS_ROTATION_ANGLE, 0.0f);
		builder.define(ZPS_ROTATION_AXIS, (byte) Direction.Axis.Y.ordinal());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void zps$saveRotation(CompoundTag tag, CallbackInfo ci) {
		tag.putFloat("ZpsRotationAngle", this.entityData.get(ZPS_ROTATION_ANGLE));
		tag.putByte("ZpsRotationAxis", this.entityData.get(ZPS_ROTATION_AXIS));
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void zps$loadRotation(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains("ZpsRotationAngle"))
			this.entityData.set(ZPS_ROTATION_ANGLE, tag.getFloat("ZpsRotationAngle"));
		if (tag.contains("ZpsRotationAxis"))
			this.entityData.set(ZPS_ROTATION_AXIS, tag.getByte("ZpsRotationAxis"));
	}

	@Override
	public void zps$setContraptionRotation(float angleDeg, Direction.Axis axis) {
		this.entityData.set(ZPS_ROTATION_ANGLE, angleDeg);
		this.entityData.set(ZPS_ROTATION_AXIS, (byte) axis.ordinal());
	}

	@Override
	public float zps$getContraptionAngle() {
		return this.entityData.get(ZPS_ROTATION_ANGLE);
	}

	@Override
	public Direction.Axis zps$getContraptionAxis() {
		return Direction.Axis.values()[this.entityData.get(ZPS_ROTATION_AXIS)];
	}
}
