package g_mungus.zps.mixin;

import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {


    @Shadow public abstract ItemStack getItem();

    public ItemEntityMixin(EntityType<?> arg, Level arg2) {
        super(arg, arg2);
    }

    @Inject(method = "setUnderwaterMovement", at = @At("HEAD"))
    private void onSetUnderwaterMovement(CallbackInfo ci) {
        ItemStack item = this.getItem();
        TagKey<Item> lithiumTag = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("zps", "lithium"));
        if (item.is(lithiumTag)) {
            Level level = this.level();
            Vec3 pos = this.position();

            level.explode(null, pos.x, pos.y, pos.z, 1f, Level.ExplosionInteraction.BLOCK);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.0);
            }
            TagKey<Item> blocksTag = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("c", "storage_blocks/lithium"));
            TagKey<Item> rawBlocksTag = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("c", "storage_blocks/raw_lithium"));
            Random random = new Random();

            if (item.is(blocksTag)) {
                spawnChildItems(level, pos, ModItems.LITHIUM_INGOT.get(), random);

            } else if (item.is(rawBlocksTag)) {
                spawnChildItems(level, pos, ModItems.RAW_LITHIUM.get(), random);
            } else {
                TagKey<Item> ingotsTag = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("c", "ingots/lithium"));

                if (item.is(ingotsTag)) {
                    spawnChildItems(level, pos, ModItems.LITHIUM_NUGGET.get(), random);
                }
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    private void spawnChildItems(Level level, Vec3 pos, Item itemToSpawn, Random random) {
        for (int i = 0; i < random.nextInt(4)+1; i++) {
            ItemEntity item = new ItemEntity(level, pos.x, pos.y+0.5, pos.z, new ItemStack(itemToSpawn));
            item.setDeltaMovement(random.nextFloat()-0.5, random.nextFloat(), random.nextFloat()-0.5);
            level.addFreshEntity(item);
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, new BlockPos((int)pos.x, (int)pos.y, (int)pos.z), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }
}
