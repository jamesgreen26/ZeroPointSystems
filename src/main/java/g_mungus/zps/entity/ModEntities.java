package g_mungus.zps.entity;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, ZPSMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<OctoMountingEntity>> OCTO_MOUNTING = ENTITIES.register("octo_mounting_seat",
        () -> EntityType.Builder.of(OctoMountingEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .build("octo_mounting_seat"));

    public static final DeferredHolder<EntityType<?>, EntityType<DodecaMountingEntity>> DODECA_MOUNTING = ENTITIES.register("dodeca_mounting_seat",
        () -> EntityType.Builder.of(DodecaMountingEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .build("dodeca_mounting_seat"));
} 
