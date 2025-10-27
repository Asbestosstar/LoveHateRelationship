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
    private Player following; // Explicitly Player type

    public VampireFollowPlayerGoal(VampireEntity vampire, double speed, float followRadius) {
        this.vampire = vampire;
        this.speedModifier = speed;
        this.followRadius = followRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Player nearest = vampire.level().getNearestPlayer(vampire, followRadius);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator()) {
            return false;
        }

        // Check relationship with the *nearest* player using the new method
        if (vampire.getRelationshipWith(nearest) <= 200) return false; // Only follow if friendly

        this.following = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Ensure following player is still valid and relationship is high enough
        if (following == null || !following.isAlive() || vampire.getRelationshipWith(following) <= 200) { // Check relationship again
            return false;
        }

        return vampire.distanceToSqr(following) > 4.0; // Stop if close
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