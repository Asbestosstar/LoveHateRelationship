// Inside the SeekShelterGoal.java file

package com.asbestosstar.lovehaterelationship.entity.goal;

import java.util.EnumSet;

import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SeekShelterGoal extends Goal {
    private final VampireEntity vampire;
    private final double speedModifier;
    private long lastSearchTime = 0;

    public SeekShelterGoal(VampireEntity vampire, double speed) {
        this.vampire = vampire;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!vampire.isInDanger()) {
            return false;
        }

        Level level = vampire.level();
        // Check if it's night time and vampire is strong/angry
        if (!level.isDay() 
            && vampire.getHealth() > 50.0F // Check if Health is over 50
            && vampire.getRelationship() <= -800) { // Check if Furious 
            // If all conditions are met, the vampire should NOT seek shelter
            return false;
        }

        int rel = vampire.getRelationship();
        if (rel <= -800 && vampire.getTarget() != null) {
            return false;
        }

        // If not in danger, or if night/strong/angry, or if furious with target, the goal won't start.
        // If still in danger and none of the above conditions apply, it can use the goal.
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Level level = vampire.level();
        // Stop seeking shelter if the conditions that prevent *starting* it now apply
        // Or if no longer in danger
        if (!level.isDay() 
            && vampire.getHealth() > 50.0F 
            && vampire.getRelationship() <= -800) { // Check if Furious
            return false; // Stop the goal if night/strong/angry
        }

        // Continue if still in danger and navigation is in progress
        return vampire.isInDanger() && vampire.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        Level level = vampire.level();
        long now = level.getGameTime();

        if (now - lastSearchTime < 20) return;
        lastSearchTime = now;

        BlockPos shelter = findNearbyShelter();
        if (shelter != null) {
            vampire.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, speedModifier);
        } else {
            // Fallback: move down to escape sunlight (only relevant during day)
            if (vampire.isOutside()) {
                BlockPos down = vampire.blockPosition().below(2);
                if (level.getBlockState(down).blocksMotion()) {
                    vampire.getNavigation().moveTo(down.getX() + 0.5, down.getY(), down.getZ() + 0.5, speedModifier);
                } else {
                    // If moving down doesn't work, try to find any nearby block that blocks motion
                    BlockPos bestFallback = findBestFallback();
                    if (bestFallback != null) {
                        vampire.getNavigation().moveTo(bestFallback.getX() + 0.5, bestFallback.getY(), bestFallback.getZ() + 0.5, speedModifier);
                    }
                }
            }
        }
    }

    private BlockPos findNearbyShelter() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Level level = vampire.level();

        // Search in a smaller radius first, focusing on the vampire's current Y level and one block above/below
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = -1; y <= 1; y++) { // Only search 1 block above and below current Y
                    pos.set(vampire.getX() + x, vampire.getY() + y, vampire.getZ() + z);
                    if (isSheltered(pos, level)) {
                        return pos.immutable(); // Return the first suitable position found
                    }
                }
            }
        }

        // If nothing found in small radius, search a larger area but only at the current Y level
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                pos.set(vampire.getX() + x, vampire.getY(), vampire.getZ() + z);
                if (isSheltered(pos, level)) {
                    return pos.immutable();
                }
            }
        }

        return null; // No shelter found
    }

    private BlockPos findBestFallback() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Level level = vampire.level();

        // Try to find a nearby block that blocks motion (within 8 blocks horizontally and 1 block vertically)
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = -1; y <= 1; y++) {
                    pos.set(vampire.getX() + x, vampire.getY() + y, vampire.getZ() + z);
                    if (level.getBlockState(pos).blocksMotion()) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null; // No fallback found
    }

    private boolean isSheltered(BlockPos pos, Level level) {
        // Must be able to stand here
        if (!level.getBlockState(pos).blocksMotion()) {
            return false;
        }
        // No water
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        return !level.canSeeSky(pos);
    }
}