
package com.asbestosstar.lovehaterelationship.entity.goal;

import java.util.EnumSet;

import com.asbestosstar.lovehaterelationship.LoveHateRelationShip;
import com.asbestosstar.lovehaterelationship.entity.VampireEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public class VampireAvoidGarlicGoal extends Goal {
    private final VampireEntity vampire;
    private final double moveSpeed;
    private BlockPos garlicPos;
    private Path pathToAvoid;

    public VampireAvoidGarlicGoal(VampireEntity vampire, double moveSpeed) {
        this.vampire = vampire;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (vampire.getTarget() != null || vampire.getNavigation().isInProgress()) {
            // Optionally allow if garlic is extremely close
        }

        int searchRange = 5;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        Level level = vampire.level();

        for (int x = -searchRange; x <= searchRange; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -searchRange; z <= searchRange; z++) {
                    checkPos.set(vampire.blockPosition()).move(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    Block block = state.getBlock();

                    if (block == LoveHateRelationShip.GARLIC_PLANT.get()) {
                        double distanceSq = vampire.distanceToSqr(
                                checkPos.getX() + 0.5,
                                checkPos.getY() + 0.5,
                                checkPos.getZ() + 0.5
                        );

                        if (distanceSq < 6.25) {
                            this.garlicPos = checkPos.immutable();

                            double dx = vampire.getX() - this.garlicPos.getX();
                            double dz = vampire.getZ() - this.garlicPos.getZ();
                            double length = Math.sqrt(dx * dx + dz * dz);
                            if (length > 0) {
                                dx /= length;
                                dz /= length;
                            }

                            int targetX = this.garlicPos.getX() + (int) Math.round(dx * 16.0);
                            int targetY = this.garlicPos.getY();
                            int targetZ = this.garlicPos.getZ() + (int) Math.round(dz * 16.0);

                            this.pathToAvoid = vampire.getNavigation().createPath(targetX, targetY, targetZ, 0);

                            if (this.pathToAvoid != null) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.pathToAvoid != null
                && this.garlicPos != null
                && vampire.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        if (this.pathToAvoid != null) {
            vampire.getNavigation().moveTo(this.pathToAvoid, this.moveSpeed);
        }
    }

    @Override
    public void stop() {
        vampire.getNavigation().stop();
        this.pathToAvoid = null;
        this.garlicPos = null;
    }

    @Override
    public void tick() {
        // Navigation handles movement automatically
    }
}
