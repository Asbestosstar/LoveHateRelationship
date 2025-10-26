
package com.asbestosstar.lovehaterelationship.entity.goal;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;

import java.util.EnumSet;

public class VampireFriendlyBiteGoal extends Goal {
    private final VampireEntity vampire;
    private Player targetPlayer;
    private int biteCooldown = 0;

    public VampireFriendlyBiteGoal(VampireEntity vampire) {
        this.vampire = vampire;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (biteCooldown > 0) {
            biteCooldown--;
            return false;
        }

        if (vampire.getRelationship() < -299) {
            return false;
        }

        Player player = vampire.level().getNearestPlayer(vampire, 6.0);
        if (player == null || player.isCreative() || player.isSpectator()) {
            return false;
        }

        LivingEntity currentTarget = vampire.getTarget();
        if (currentTarget != null && currentTarget != player) {
            return false;
        }

        this.targetPlayer = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPlayer != null &&
               targetPlayer.isAlive() &&
               vampire.getRelationship() >= -299 &&
               vampire.distanceToSqr(targetPlayer) <= 4.0;
    }

    @Override
    public void start() {
        vampire.setTarget(targetPlayer);
        vampire.getNavigation().moveTo(targetPlayer, 1.0);
    }

    @Override
    public void stop() {
        vampire.getNavigation().stop();
        vampire.setTarget(null);
        targetPlayer = null;
        biteCooldown = 300;
    }

    @Override
    public void tick() {
        if (targetPlayer != null && targetPlayer.isAlive()) {
            if (vampire.distanceToSqr(targetPlayer) <= 4.0) {
                performBite();
                vampire.getNavigation().stop();
                vampire.setTarget(null);
                targetPlayer = null;
                biteCooldown = 300;
            } else {
                vampire.getNavigation().moveTo(targetPlayer, 1.0);
            }
        }
    }

    private void performBite() {
        if (targetPlayer != null && targetPlayer.isAlive() && !vampire.level().isClientSide) {
            DamageSource biteDamageSource = vampire.damageSources().mobAttack(vampire);
            targetPlayer.hurt(biteDamageSource, 4.0f);
            vampire.adjustRelationship(200);
            vampire.playSound(net.minecraft.sounds.SoundEvents.PLAYER_BREATH, 1.0f, 1.0f);
        }
    }
}
