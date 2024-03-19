package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SwimNodeEvaluator extends NodeEvaluator {
    private final boolean allowBreaching;
    private final Long2ObjectMap<BlockPathTypes> pathTypesByPosCache = new Long2ObjectOpenHashMap<>();

    public SwimNodeEvaluator(boolean canJumpOutOfWater) {
        this.allowBreaching = canJumpOutOfWater;
    }

    @Override
    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        super.prepare(cachedWorld, entity);
        this.pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        return this.getNode(Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5D), Mth.floor(this.mob.getBoundingBox().minZ));
    }

    @Override
    public Target getGoal(double x, double y, double z) {
        return this.getTargetFromNode(this.getNode(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        int i = 0;
        Map<Direction, Node> map = Maps.newEnumMap(Direction.class);

        for(Direction direction : Direction.values()) {
            Node node2 = this.findAcceptedNode(node.x + direction.getStepX(), node.y + direction.getStepY(), node.z + direction.getStepZ());
            map.put(direction, node2);
            if (this.isNodeValid(node2)) {
                successors[i++] = node2;
            }
        }

        for(Direction direction2 : Direction.Plane.HORIZONTAL) {
            Direction direction3 = direction2.getClockWise();
            Node node3 = this.findAcceptedNode(node.x + direction2.getStepX() + direction3.getStepX(), node.y, node.z + direction2.getStepZ() + direction3.getStepZ());
            if (this.isDiagonalNodeValid(node3, map.get(direction2), map.get(direction3))) {
                successors[i++] = node3;
            }
        }

        return i;
    }

    protected boolean isNodeValid(@Nullable Node node) {
        return node != null && !node.closed;
    }

    protected boolean isDiagonalNodeValid(@Nullable Node diagonalNode, @Nullable Node node1, @Nullable Node node2) {
        return this.isNodeValid(diagonalNode) && node1 != null && node1.costMalus >= 0.0F && node2 != null && node2.costMalus >= 0.0F;
    }

    @Nullable
    protected Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        BlockPathTypes blockPathTypes = this.getCachedBlockType(x, y, z);
        if (this.allowBreaching && blockPathTypes == BlockPathTypes.BREACH || blockPathTypes == BlockPathTypes.WATER) {
            float f = this.mob.getPathfindingMalus(blockPathTypes);
            if (f >= 0.0F) {
                node = this.getNode(x, y, z);
                node.type = blockPathTypes;
                node.costMalus = Math.max(node.costMalus, f);
                if (this.level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    node.costMalus += 8.0F;
                }
            }
        }

        return node;
    }

    protected BlockPathTypes getCachedBlockType(int x, int y, int z) {
        return this.pathTypesByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), (pos) -> {
            return this.getBlockPathType(this.level, x, y, z);
        });
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z) {
        return this.getBlockPathType(world, x, y, z, this.mob);
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z, Mob mob) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(int i = x; i < x + this.entityWidth; ++i) {
            for(int j = y; j < y + this.entityHeight; ++j) {
                for(int k = z; k < z + this.entityDepth; ++k) {
                    FluidState fluidState = world.getFluidState(mutableBlockPos.set(i, j, k));
                    BlockState blockState = world.getBlockState(mutableBlockPos.set(i, j, k));
                    if (fluidState.isEmpty() && blockState.isPathfindable(world, mutableBlockPos.below(), PathComputationType.WATER) && blockState.isAir()) {
                        return BlockPathTypes.BREACH;
                    }

                    if (!fluidState.is(FluidTags.WATER)) {
                        return BlockPathTypes.BLOCKED;
                    }
                }
            }
        }

        BlockState blockState2 = world.getBlockState(mutableBlockPos);
        return blockState2.isPathfindable(world, mutableBlockPos, PathComputationType.WATER) ? BlockPathTypes.WATER : BlockPathTypes.BLOCKED;
    }
}
