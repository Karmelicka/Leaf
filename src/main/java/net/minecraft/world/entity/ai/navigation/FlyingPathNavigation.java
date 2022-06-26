package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.async.path.NodeEvaluatorFeatures;
import org.dreeam.leaf.async.path.NodeEvaluatorGenerator;

public class FlyingPathNavigation extends PathNavigation {
    public FlyingPathNavigation(Mob entity, Level world) {
        super(entity, world);
    }

    // Kaiiju start - petal - async path processing
    private static final NodeEvaluatorGenerator nodeEvaluatorGenerator = (NodeEvaluatorFeatures nodeEvaluatorFeatures) -> {
        FlyNodeEvaluator nodeEvaluator = new FlyNodeEvaluator();
        nodeEvaluator.setCanPassDoors(nodeEvaluatorFeatures.canPassDoors());
        nodeEvaluator.setCanFloat(nodeEvaluatorFeatures.canFloat());
        nodeEvaluator.setCanWalkOverFences(nodeEvaluatorFeatures.canWalkOverFences());
        nodeEvaluator.setCanOpenDoors(nodeEvaluatorFeatures.canOpenDoors());
        return nodeEvaluator;
    };
    // Kaiiju end

    @Override
    protected PathFinder createPathFinder(int range) {
        this.nodeEvaluator = new FlyNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        // Kaiiju start - petal - async path processing
        if (org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled)
            return new PathFinder(this.nodeEvaluator, range, nodeEvaluatorGenerator);
        else
        // Kaiiju end
        return new PathFinder(this.nodeEvaluator, range);
    }

    @Override
    protected boolean canMoveDirectly(Vec3 origin, Vec3 target) {
        return isClearForMovementBetween(this.mob, origin, target, true);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.canFloat() && this.mob.isInLiquid() || !this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override
    public Path createPath(Entity entity, int distance) {
        return this.createPath(entity.blockPosition(), entity, distance); // Paper - EntityPathfindEvent
    }

    @Override
    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        if (this.path != null && !this.path.isProcessed()) return; // Kaiiju - petal - async path processing

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 vec3 = this.path.getNextEntityPos(this.mob);
                if (this.mob.getBlockX() == Mth.floor(vec3.x) && this.mob.getBlockY() == Mth.floor(vec3.y) && this.mob.getBlockZ() == Mth.floor(vec3.z)) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
            if (!this.isDone()) {
                Vec3 vec32 = this.path.getNextEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(vec32.x, vec32.y, vec32.z, this.speedModifier);
            }
        }
    }

    public void setCanOpenDoors(boolean canPathThroughDoors) {
        this.nodeEvaluator.setCanOpenDoors(canPathThroughDoors);
    }

    public boolean canPassDoors() {
        return this.nodeEvaluator.canPassDoors();
    }

    public void setCanPassDoors(boolean canEnterOpenDoors) {
        this.nodeEvaluator.setCanPassDoors(canEnterOpenDoors);
    }

    public boolean canOpenDoors() {
        return this.nodeEvaluator.canPassDoors();
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return this.level.getBlockState(pos).entityCanStandOn(this.level, pos, this.mob);
    }
}
