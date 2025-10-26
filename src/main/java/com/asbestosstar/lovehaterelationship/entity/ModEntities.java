package com.asbestosstar.lovehaterelationship.entity;

import com.asbestosstar.lovehaterelationship.LoveHateRelationShip;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, LoveHateRelationShip.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<VampireEntity>> VAMPIRE =
        ENTITY_TYPES.register("vampire", () ->
            EntityType.Builder.of(VampireEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build("vampire")
        );
}