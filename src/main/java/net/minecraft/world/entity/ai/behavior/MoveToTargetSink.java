package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class MoveToTargetSink extends Behavior<Mob> {
    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;
    private int remainingCooldown;
    @Nullable
    private Path path;
    private boolean finishedProcessing; // Kaiiju - petal - track when path is processed
    @Nullable
    private BlockPos lastTargetPos;
    private float speedModifier;

    public MoveToTargetSink() {
        this(150, 250);
    }

    public MoveToTargetSink(int minRunTime, int maxRunTime) {
        super(ImmutableMap.of(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED, MemoryModuleType.PATH, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT), minRunTime, maxRunTime);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
        if (this.remainingCooldown > 0) {
            --this.remainingCooldown;
            return false;
        } else {
            Brain<?> brain = entity.getBrain();
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            boolean bl = this.reachedTarget(entity, walkTarget);
            if (!org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled && !bl && this.tryComputePath(entity, walkTarget, world.getGameTime())) { // Kaiiju - petal - async path processing means we can't know if the path is reachable here
                this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
                return true;
            } else if (org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled && !bl) { return true; // Kaiiju - async pathfinding
            } else {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                if (bl) {
                    brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                }

                return false;
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Mob entity, long time) {
        if (org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled && !this.finishedProcessing) return true; // Kaiiju - petal - wait for processing
        if (this.path != null && this.lastTargetPos != null) {
            Optional<WalkTarget> optional = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean bl = optional.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);
            PathNavigation pathNavigation = entity.getNavigation();
            return !pathNavigation.isDone() && optional.isPresent() && !this.reachedTarget(entity, optional.get()) && !bl;
        } else {
            return false;
        }
    }

    @Override
    protected void stop(ServerLevel world, Mob entity, long time) {
        if (entity.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && !this.reachedTarget(entity, entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get()) && entity.getNavigation().isStuck()) {
            this.remainingCooldown = world.getRandom().nextInt(40);
        }

        entity.getNavigation().stop();
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.PATH);
        this.path = null;
    }

    @Override
    protected void start(ServerLevel serverLevel, Mob mob, long l) {
        // Kaiiju start - petal - start processing
        if (org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled) {
            Brain<?> brain = mob.getBrain();
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();

            this.finishedProcessing = false;
            this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
            this.path = this.computePath(mob, walkTarget);
            return;
        }
        // Kaiiju end
        mob.getBrain().setMemory(MemoryModuleType.PATH, this.path);
        mob.getNavigation().moveTo(this.path, (double)this.speedModifier);
    }

    @Override
    protected void tick(ServerLevel serverLevel, Mob mob, long l) {
        // Kaiiju start - petal - Async path processing
        if (org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled) {
            if (this.path != null && !this.path.isProcessed()) return; // wait for processing

            if (!this.finishedProcessing) {
                this.finishedProcessing = true;

                Brain<?> brain = mob.getBrain();
                boolean canReach = this.path != null && this.path.canReach();
                if (canReach) {
                    brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                    brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, l);
                }

                if (!canReach) {
                    Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);

                    if (!walkTarget.isPresent()) return;

                    BlockPos blockPos = walkTarget.get().getTarget().currentBlockPosition();
                    Vec3 vec3 = DefaultRandomPos.getPosTowards((PathfinderMob) mob, 10, 7, Vec3.atBottomCenterOf(blockPos), (float) Math.PI / 2F);
                    if (vec3 != null) {
                        // try recalculating the path using a random position
                        this.path = mob.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
                        this.finishedProcessing = false;
                        return;
                    }
                }

                mob.getBrain().setMemory(MemoryModuleType.PATH, this.path);
                mob.getNavigation().moveTo(this.path, this.speedModifier);
            }

            Path path = mob.getNavigation().getPath();
            Brain<?> brain = mob.getBrain();

            if (path != null && this.lastTargetPos != null && brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get(); // we know isPresent = true
                if (walkTarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0D) {
                    this.start(serverLevel, mob, l);
                }
            }
        } else {
        // Kaiiju end
        Path path = mob.getNavigation().getPath();
        Brain<?> brain = mob.getBrain();
        if (this.path != path) {
            this.path = path;
            brain.setMemory(MemoryModuleType.PATH, path);
        }

        if (path != null && this.lastTargetPos != null) {
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            if (walkTarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0D && this.tryComputePath(mob, walkTarget, serverLevel.getGameTime())) {
                this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
                this.start(serverLevel, mob, l);
            }

        }
        } // Kaiiju - async path processing
    }

    // Kaiiju start - petal - Async path processing
    @Nullable
    private Path computePath(Mob entity, WalkTarget walkTarget) {
        BlockPos blockPos = walkTarget.getTarget().currentBlockPosition();
        // don't pathfind outside region
        //if (!io.papermc.paper.util.TickThread.isTickThreadFor((ServerLevel) entity.level(), blockPos)) return null; // Leaf - Don't need this
        this.speedModifier = walkTarget.getSpeedModifier();
        Brain<?> brain = entity.getBrain();
        if (this.reachedTarget(entity, walkTarget)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        return entity.getNavigation().createPath(blockPos, 0);
    }
    // Kaiiju end

    private boolean tryComputePath(Mob entity, WalkTarget walkTarget, long time) {
        BlockPos blockPos = walkTarget.getTarget().currentBlockPosition();
        this.path = entity.getNavigation().createPath(blockPos, 0);
        this.speedModifier = walkTarget.getSpeedModifier();
        Brain<?> brain = entity.getBrain();
        if (this.reachedTarget(entity, walkTarget)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            boolean bl = this.path != null && this.path.canReach();
            if (bl) {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, time);
            }

            if (this.path != null) {
                return true;
            }

            Vec3 vec3 = DefaultRandomPos.getPosTowards((PathfinderMob)entity, 10, 7, Vec3.atBottomCenterOf(blockPos), (double)((float)Math.PI / 2F));
            if (vec3 != null) {
                this.path = entity.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
                return this.path != null;
            }
        }

        return false;
    }

    private boolean reachedTarget(Mob entity, WalkTarget walkTarget) {
        return walkTarget.getTarget().currentBlockPosition().distManhattan(entity.blockPosition()) <= walkTarget.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(WalkTarget target) {
        PositionTracker positionTracker = target.getTarget();
        if (positionTracker instanceof EntityTracker entityTracker) {
            return entityTracker.getEntity().isSpectator();
        } else {
            return false;
        }
    }
}
