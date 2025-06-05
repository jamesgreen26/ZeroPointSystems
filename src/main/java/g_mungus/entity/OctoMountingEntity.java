package g_mungus.entity;

import g_mungus.blockentity.OctoControllerBlockEntity;
import g_mungus.client.ModKeybinds;
import g_mungus.networking.OctovariantControlPacket;
import g_mungus.networking.ZPSGamePackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class OctoMountingEntity extends Entity {
    public OctoControllerBlockEntity blockEntity = null;
    public boolean isController = false;

    public OctoMountingEntity(@NotNull EntityType<OctoMountingEntity> type, @NotNull Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        this.baseTick();

        Level level = this.level();

        if (!level.isClientSide && this.getPassengers().isEmpty()) {
            // Kill this entity if nothing is riding it
            kill();
            return;
        }

        sendControlPacket();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag arg) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag arg) {}

    @Override
    protected void defineSynchedData() {}

    private void sendControlPacket() {
        if (!level().isClientSide || blockEntity == null) return;

        int a = ModKeybinds.KEY_A.isDown()? 15 : 0;
        int b = ModKeybinds.KEY_B.isDown()? 15 : 0;
        int c = ModKeybinds.KEY_C.isDown()? 15 : 0;
        int d = ModKeybinds.KEY_D.isDown()? 15 : 0;
        int e = ModKeybinds.KEY_E.isDown()? 15 : 0;
        int f = ModKeybinds.KEY_F.isDown()? 15 : 0;
        int g = ModKeybinds.KEY_G.isDown()? 15 : 0;
        int h = ModKeybinds.KEY_H.isDown()? 15 : 0;

        ZPSGamePackets.INSTANCE.sendToServer(new OctovariantControlPacket(a, b, c, d, e, f, g, h));
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity pLivingEntity) {
        return super.getDismountLocationForPassenger(pLivingEntity).add(0, 0.5f, 0);
    }

    @Override
    public LivingEntity getControllingPassenger() {
         if (isController && !this.getPassengers().isEmpty()) {
             return (LivingEntity) this.getPassengers().get(0);
        } else {
             return null;
        }
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
