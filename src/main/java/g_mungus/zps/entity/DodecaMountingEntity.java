package g_mungus.zps.entity;

import g_mungus.zps.block.cableNetwork.DodecaControllerBlock;
import g_mungus.zps.blockentity.DodecaControllerBlockEntity;
import g_mungus.zps.client.ModKeybinds;
import g_mungus.zps.networking.DodecaControlPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class DodecaMountingEntity extends Entity {
    public DodecaControllerBlockEntity blockEntity = null;
    public boolean isController = false;
    public Vec3i offset = Vec3i.ZERO;

    public DodecaMountingEntity(@NotNull EntityType<DodecaMountingEntity> type, @NotNull Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        this.baseTick();

        if (level().isClientSide) {
            sendControlPacket();
            return;
        }

        if (!verifyControllerBlock()) {
            cleanupAndDiscard();
        }
    }

    private boolean verifyControllerBlock() {
        if (isNextToControllerBlock(offset)) {
            ensureBlockEntityLinked();
            return !getPassengers().isEmpty();
        }

        if (searchAdjacentForController()) {
            return !getPassengers().isEmpty();
        }

        return false;
    }

    private boolean isNextToControllerBlock(Vec3i testOffset) {
        BlockState state = level().getBlockState(blockPosition().offset(testOffset));
        return state.getBlock() instanceof DodecaControllerBlock &&
                state.getValue(DodecaControllerBlock.FACING).getOpposite().getNormal().subtract(testOffset).distManhattan(Vec3i.ZERO) == 0;
    }

    private boolean searchAdjacentForController() {
        Vec3i[] adjacentOffsets = {
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1)
        };

        for (Vec3i testOffset : adjacentOffsets) {
            if (isNextToControllerBlock(testOffset)) {
                offset = testOffset;
                ensureBlockEntityLinked();
                return true;
            }
        }
        return false;
    }

    private void ensureBlockEntityLinked() {
        if (blockEntity == null) {
            if (level().getBlockEntity(blockPosition().offset(offset)) instanceof DodecaControllerBlockEntity newBlockEntity) {
                blockEntity = newBlockEntity;
                isController = true;
            }
        }
    }

    private void cleanupAndDiscard() {
        if (blockEntity != null) {
            blockEntity.setA(0);
            blockEntity.setB(0);
            blockEntity.setC(0);
            blockEntity.setD(0);
            blockEntity.setE(0);
            blockEntity.setF(0);
            blockEntity.setG(0);
            blockEntity.setH(0);
            blockEntity.setI(0);
            blockEntity.setJ(0);
            blockEntity.setK(0);
            blockEntity.setL(0);
        }
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag arg) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    private void sendControlPacket() {
        if (!level().isClientSide) return;

        int a = ModKeybinds.KEY_A.isDown() ? 15 : 0;
        int b = ModKeybinds.KEY_B.isDown() ? 15 : 0;
        int c = ModKeybinds.KEY_C.isDown() ? 15 : 0;
        int d = ModKeybinds.KEY_D.isDown() ? 15 : 0;
        int e = ModKeybinds.KEY_E.isDown() ? 15 : 0;
        int f = ModKeybinds.KEY_F.isDown() ? 15 : 0;
        int g = ModKeybinds.KEY_G.isDown() ? 15 : 0;
        int h = ModKeybinds.KEY_H.isDown() ? 15 : 0;
        int i = ModKeybinds.KEY_I.isDown() ? 15 : 0;
        int j = ModKeybinds.KEY_J.isDown() ? 15 : 0;
        int k = ModKeybinds.KEY_K.isDown() ? 15 : 0;
        int l = ModKeybinds.KEY_L.isDown() ? 15 : 0;

        ZPSGamePackets.sendToServer(new DodecaControlPacket(a, b, c, d, e, f, g, h, i, j, k, l));
    }

    @Override
    public LivingEntity getControllingPassenger() {
         if (isController && !this.getPassengers().isEmpty()) {
             return (LivingEntity) this.getPassengers().get(0);
        } else {
             return null;
        }
    }
}
