package g_mungus.zps.entity;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZPSMod.MOD_ID);

    public static final RegistryObject<EntityType<OctoMountingEntity>> OCTO_MOUNTING = ENTITIES.register("octo_mounting_seat",
        () -> EntityType.Builder.of(OctoMountingEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .build("octo_mounting_seat"));

    public static final RegistryObject<EntityType<DodecaMountingEntity>> DODECA_MOUNTING = ENTITIES.register("dodeca_mounting_seat",
        () -> EntityType.Builder.of(DodecaMountingEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .build("dodeca_mounting_seat"));
} 
