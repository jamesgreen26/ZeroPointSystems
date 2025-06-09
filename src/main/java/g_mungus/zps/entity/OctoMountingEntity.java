package g_mungus.zps.entity;

import g_mungus.zps.blockentity.OctoControllerBlockEntity;
import g_mungus.zps.client.ModKeybinds;
import g_mungus.zps.networking.OctovariantControlPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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

            if (blockEntity != null) {
                blockEntity.setA(0);
                blockEntity.setB(0);
                blockEntity.setC(0);
                blockEntity.setD(0);
                blockEntity.setE(0);
                blockEntity.setF(0);
                blockEntity.setG(0);
                blockEntity.setH(0);
            }

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
        if (!level().isClientSide) return;

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
