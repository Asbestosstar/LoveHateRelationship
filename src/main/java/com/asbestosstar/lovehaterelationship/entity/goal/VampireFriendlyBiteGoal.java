package com.asbestosstar.lovehaterelationship.entity.goal;

import java.util.EnumSet;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

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

        // Check relationship with the nearest playe
        Player player = vampire.level().getNearestPlayer(vampire, 6.0);
        if (player == null || player.isCreative() || player.isSpectator()) {
            return false;
        }

        // Use relationship with the nearest player
        if (vampire.getRelationshipWith(player) < -299) { 
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
        // Ensure targetPlayer is still valid and relationship is high enough
        if (targetPlayer == null || !targetPlayer.isAlive() || vampire.getRelationshipWith(targetPlayer) < -299) { // Check relationship again
            return false;
        }

        return vampire.distanceToSqr(targetPlayer) <= 4.0;
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
            vampire.adjustRelationshipWith(targetPlayer, 200); // Use adjustRelationshipWith
            //vampire.playSound(net.minecraft.sounds.SoundEvents.PLAYER_BREATH, 1.0f, 1.0f);
            vampire.level().playSound(targetPlayer, vampire.blockPosition(), BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                    BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("lovehaterelationship", "vampire_bite"))).value(),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        
        }
    }
}