package net.eclipce.transpondersnails.entity.custom;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.core.BlockPos;

public class UnderwaterWalkingGoal extends Goal {
    protected final PathfinderMob mob;
    private final double speed;

    public UnderwaterWalkingGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        return this.mob.isInWater();
    }

    @Override
    public void start() {
        if (this.mob.isInWater()) {
            BlockPos target = this.mob.blockPosition().offset(
                    this.mob.getRandom().nextInt(10) - 5,
                    0,
                    this.mob.getRandom().nextInt(10) - 5
            );
            this.mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), this.speed);
        }
    }

    @Override
    public void tick() {
        if (this.mob.getNavigation().isDone()) {
            // Re-set target periodically
            BlockPos target = this.mob.blockPosition().offset(
                    this.mob.getRandom().nextInt(10) - 5,
                    0,
                    this.mob.getRandom().nextInt(10) - 5
            );
            this.mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), this.speed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}