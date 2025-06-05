package g_mungus.entity;

import g_mungus.ZPSMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZPSMod.MOD_ID);

    public static final RegistryObject<EntityType<OctoMountingEntity>> OCTO_MOUNTING = ENTITIES.register("octo_mounting_seat",
        () -> EntityType.Builder.<OctoMountingEntity>of((type, level) -> new OctoMountingEntity(type, level), MobCategory.MISC)
            .sized(0.6f, 0.6f)
            .build("octo_mounting_seat"));
} 