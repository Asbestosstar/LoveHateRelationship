package com.asbestosstar.lovehaterelationship.entity.goal;

import java.util.EnumSet;
import com.asbestosstar.lovehaterelationship.entity.VampireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

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
        LivingEntity currentTarget = vampire.getTarget();
        int relToTarget = (currentTarget != null) ? vampire.getRelationshipWith(currentTarget) : 0;

        if (!level.isDay() && vampire.getHealth() > 50.0F && relToTarget <= -800) {
            return false;
        }

        if (relToTarget <= -800 && currentTarget != null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Level level = vampire.level();
        LivingEntity currentTarget = vampire.getTarget();
        int relToTarget = (currentTarget != null) ? vampire.getRelationshipWith(currentTarget) : 0;

        if (!level.isDay() && vampire.getHealth() > 50.0F && relToTarget <= -800) {
            return false;
        }

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
            if (vampire.isOutside()) {
                BlockPos down = vampire.blockPosition().below(2);
                if (level.getBlockState(down).blocksMotion()) {
                    vampire.getNavigation().moveTo(down.getX() + 0.5, down.getY(), down.getZ() + 0.5, speedModifier);
                } else {
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

        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = -1; y <= 1; y++) {
                    pos.set(vampire.getX() + x, vampire.getY() + y, vampire.getZ() + z);
                    if (isSheltered(pos, level)) {
                        return pos.immutable();
                    }
                }
            }
        }

        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                pos.set(vampire.getX() + x, vampire.getY(), vampire.getZ() + z);
                if (isSheltered(pos, level)) {
                    return pos.immutable();
                }
            }
        }

        return null;
    }

    private BlockPos findBestFallback() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Level level = vampire.level();

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
        return null;
    }

    private boolean isSheltered(BlockPos pos, Level level) {
        if (!level.getBlockState(pos).blocksMotion()) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        return !level.canSeeSky(pos);
    }
}