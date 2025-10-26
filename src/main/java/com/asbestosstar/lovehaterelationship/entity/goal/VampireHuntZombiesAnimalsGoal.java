package com.asbestosstar.lovehaterelationship.entity.goal;

import java.util.EnumSet;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class VampireHuntZombiesAnimalsGoal extends Goal {
    private final VampireEntity vampire;
    private LivingEntity target;

    private static final TargetingConditions HUNTING_TARGETING = TargetingConditions.forCombat()
            .range(32.0)
            .ignoreLineOfSight();

    public VampireHuntZombiesAnimalsGoal(VampireEntity vampire) {
        this.vampire = vampire;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Level level = vampire.level();
        this.target = level.getNearestEntity(
                LivingEntity.class,
                HUNTING_TARGETING,
                vampire,
                vampire.getX(),
                vampire.getY(),
                vampire.getZ(),
                vampire.getBoundingBox().inflate(32.0)
        );

        return target != null &&
               target.isAlive() &&
               (target.getType() == EntityType.ZOMBIE || target instanceof Animal);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null &&
               target.isAlive() &&
               (target.getType() == EntityType.ZOMBIE || target instanceof Animal) &&
               vampire.distanceToSqr(target) > 2.0;
    }

    @Override
    public void start() {
        vampire.setTarget(target);
    }

    @Override
    public void tick() {
        if (this.target != null && vampire.getTarget() != this.target) {
            vampire.setTarget(this.target);
        }
    }

    @Override
    public void stop() {
        vampire.setTarget(null);
        this.target = null;
    }
}
