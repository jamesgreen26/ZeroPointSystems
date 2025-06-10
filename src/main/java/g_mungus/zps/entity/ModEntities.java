package g_mungus.zps.entity;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, ZPSMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<OctoMountingEntity>> OCTO_MOUNTING = ENTITIES.register("octo_mounting_seat",
        () -> EntityType.Builder.<OctoMountingEntity>of(OctoMountingEntity::new, MobCategory.MISC)
            .sized(0.6f, 0.6f)
            .build("octo_mounting_seat"));
} 