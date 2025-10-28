package com.asbestosstar.lovehaterelationship.entity.goal;

import java.lang.reflect.Method;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class VampireMountVampireLlamaGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(VampireMountVampireLlamaGoal.class);

    private final VampireEntity vampire;
    private LivingEntity targetLlama; // The potential mount target

    private static final TargetingConditions MOUNT_TARGETING = TargetingConditions.forNonCombat()
            .range(16.0) // Search within 16 blocks
            .ignoreLineOfSight(); // Don't require direct line of sight

    // Reflection cache for Vampiric Llamas mod
    private static boolean vampiricLlamaModFound = false;
    private static Class<?> vampiricLlamaClass = null;
    private static Method mountMethod = null; // For startRiding

    // Static block to initialize reflection for Vampiric Llamas mod
    static {
        if (ModList.get().isLoaded("vampiricllamas")) {
            try {
                vampiricLlamaClass = Class.forName("com.startraveler.vampiricllamas.entity.VampireLlama");
                vampiricLlamaModFound = true;

                // Find the method to mount: startRiding(Entity entityToRide)
                // Note: startRiding is inherited from Entity
                
                mountMethod = Entity.class.getDeclaredMethod("startRiding", net.minecraft.world.entity.Entity.class);
                mountMethod.setAccessible(true); // Ensure access if needed

                LOGGER.info("Successfully reflected VampiricLlama class and startRiding method for VampireMountVampireLlamaGoal.");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                LOGGER.warn("Could not reflect VampiricLlama class or startRiding method for VampireMountVampireLlamaGoal: {}", e.getMessage());
                vampiricLlamaModFound = false;
            }
        } else {
            LOGGER.info("VampiricLlamas mod not loaded, skipping reflection setup for VampireMountVampireLlamaGoal.");
        }
    }

    public VampireMountVampireLlamaGoal(VampireEntity vampire) {
        this.vampire = vampire;
        // Set flags appropriately, e.g., if this goal moves the vampire significantly
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); // Adjust flags based on behavior
    }

    @Override
    public boolean canUse() {
        // Check if mod is available and reflection succeeded
        if (!vampiricLlamaModFound || vampiricLlamaClass == null || mountMethod == null) {
            return false; // Cannot use if mod isn't present or reflection failed
        }

        // Check if already riding something
        if (vampire.getVehicle() != null) {
            // Optionally, check if already riding a VampireLlama
            if (vampiricLlamaClass.isInstance(vampire.getVehicle())) {
                 // Already riding a VampireLlama, no need to find another one
                 return false;
            }
            // If riding something else, this goal might not be applicable
            // Return false if it should only mount VampireLlamas and not dismount others
            return false;
        }

        Level level = vampire.level();
        this.targetLlama = level.getNearestEntity(
                LivingEntity.class, // Search for LivingEntity
                MOUNT_TARGETING,
                vampire, // Exclusion entity (don't target self)
                vampire.getX(),
                vampire.getY(),
                vampire.getZ(),
                vampire.getBoundingBox().inflate(16.0) // Search area
        );

        // Check if a valid VampireLlama was found and is suitable for mounting
        return targetLlama != null &&
               targetLlama.isAlive() &&
               vampiricLlamaClass.isInstance(targetLlama) && // Confirm it's a VampireLlama
               targetLlama.getVehicle() == null; // Ensure the llama isn't already ridden
    }

    @Override
    public boolean canContinueToUse() {
        // Continue if we have a target llama and it's still valid
        return targetLlama != null &&
               targetLlama.isAlive() &&
               vampiricLlamaClass.isInstance(targetLlama) &&
               targetLlama.getVehicle() == null && // Ensure llama isn't taken
               vampire.getVehicle() == null; // Ensure vampire isn't riding something else
    }

    @Override
    public void start() {
        // Start moving towards the target llama
        if (targetLlama != null) {
            vampire.getNavigation().moveTo(targetLlama, 1.2); // Adjust speed as needed
        }
    }

    @Override
    public void tick() {
        if (targetLlama == null || !targetLlama.isAlive() || !vampiricLlamaClass.isInstance(targetLlama)) {
            // Target is invalid, stop trying to mount
            vampire.getNavigation().stop();
            this.targetLlama = null;
            return;
        }

        // If close enough, attempt to mount
        if (vampire.distanceToSqr(targetLlama) < 4.0) { // Within 2 blocks
            vampire.getNavigation().stop(); // Stop moving
            attemptMount();
        }
    }

    @Override
    public void stop() {
        // Stop moving if the goal is interrupted
        vampire.getNavigation().stop();
        this.targetLlama = null;
    }

    private void attemptMount() {
        if (targetLlama != null && targetLlama.isAlive() && vampiricLlamaClass.isInstance(targetLlama) && targetLlama.getVehicle() == null) {
            try {
                // Attempt to mount the llama using reflection
            	vampire.startRiding(targetLlama);
                //mountMethod.invoke(vampire, targetLlama);
                LOGGER.info("Vampire {} successfully mounted VampireLlama {} via goal.", vampire.getStringUUID(), targetLlama.getStringUUID());
            } catch (Exception e) {
                LOGGER.error("Error mounting VampireLlama via reflection in goal: {}", e.getMessage(), e);
                // Optionally, handle failure (e.g., retry, wait longer)
            }
        }
        // Clear the target regardless of success/failure
        this.targetLlama = null;
    }
}