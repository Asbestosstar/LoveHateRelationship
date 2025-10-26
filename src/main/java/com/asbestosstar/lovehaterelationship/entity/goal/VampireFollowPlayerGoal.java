package com.asbestosstar.lovehaterelationship.entity.goal;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class VampireFollowPlayerGoal extends Goal {
    private final VampireEntity vampire;
    private final double speedModifier;
    private final float followRadius;
    private LivingEntity following;

    public VampireFollowPlayerGoal(VampireEntity vampire, double speed, float followRadius) {
        this.vampire = vampire;
        this.speedModifier = speed;
        this.followRadius = followRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vampire.getRelationship() <= 200) return false; // Only follow if friendly

        Player nearest = vampire.level().getNearestPlayer(vampire, followRadius);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator()) {
            return false;
        }

        this.following = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return following != null &&
               following.isAlive() &&
               vampire.getRelationship() > 200 &&
               vampire.distanceToSqr(following) > 4.0; // Stop if close
    }

    @Override
    public void start() {
        vampire.getNavigation().moveTo(following, speedModifier);
    }

    @Override
    public void tick() {
        if (vampire.distanceToSqr(following) > 6.0) {
            vampire.getNavigation().moveTo(following, speedModifier);
        }
    }

    @Override
    public void stop() {
        this.following = null;
        vampire.getNavigation().stop();
    }
}