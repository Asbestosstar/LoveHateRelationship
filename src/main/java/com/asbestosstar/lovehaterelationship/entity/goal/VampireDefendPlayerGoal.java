package com.asbestosstar.lovehaterelationship.entity.goal;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class VampireDefendPlayerGoal extends Goal {
    private final VampireEntity vampire;
    private LivingEntity target;

    public VampireDefendPlayerGoal(VampireEntity vampire) {
        this.vampire = vampire;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (vampire.getRelationship() <= 300) {
            return false;
        }

        Player owner = vampire.level().getNearestPlayer(vampire, 32.0);
        if (owner == null || owner.isCreative() || owner.isSpectator()) {
            return false;
        }

        LivingEntity playerAttacker = owner.getLastHurtByMob();
        if (playerAttacker != null && playerAttacker.isAlive() && playerAttacker instanceof Monster) {
            this.target = playerAttacker;
            return true;
        }

        LivingEntity playerTarget = owner.getLastHurtMob();
        if (playerTarget != null && playerTarget.isAlive() && playerTarget instanceof Monster) {
            this.target = playerTarget;
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null &&
               this.target.isAlive() &&
               vampire.getRelationship() > 300 &&
               vampire.distanceToSqr(this.target) > 2.0;
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
