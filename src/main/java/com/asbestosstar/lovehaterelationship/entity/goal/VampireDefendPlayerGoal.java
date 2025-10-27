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
    private Player owner;

    public VampireDefendPlayerGoal(VampireEntity vampire) {
        this.vampire = vampire;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        this.owner = vampire.level().getNearestPlayer(vampire, 32.0);
        if (owner == null || owner.isCreative() || owner.isSpectator()) {
            return false;
        }

        if (vampire.getRelationshipWith(owner) <= 300) {
            return false;
        }

        LivingEntity playerAttacker = owner.getLastHurtByMob();
        if (playerAttacker != null && playerAttacker.isAlive() && playerAttacker instanceof Monster && playerAttacker != vampire) {
            this.target = playerAttacker;
            return true;
        }

        LivingEntity playerTarget = owner.getLastHurtMob();
        if (playerTarget != null && playerTarget.isAlive() && playerTarget instanceof Monster && playerTarget != vampire) {
            this.target = playerTarget;
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || vampire.getRelationshipWith(owner) <= 300) {
            return false;
        }

        if (this.target == null || !this.target.isAlive() || this.target == this.vampire) {
            return false;
        }

        return vampire.distanceToSqr(this.target) > 2.0;
    }

    @Override
    public void start() {
        if (this.target != null && this.target != this.vampire) {
            vampire.setTarget(target);
        }
    }

    @Override
    public void tick() {
        if (this.target != null && this.target != this.vampire && vampire.getTarget() != this.target) {
            vampire.setTarget(this.target);
        }
    }

    @Override
    public void stop() {
        if (vampire.getTarget() == this.target) {
             vampire.setTarget(null);
        }
        this.target = null;
        this.owner = null;
    }
}