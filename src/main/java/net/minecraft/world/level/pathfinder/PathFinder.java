package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Lists;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.dreeam.leaf.async.path.AsyncPath;
import org.dreeam.leaf.async.path.NodeEvaluatorCache;
import org.dreeam.leaf.async.path.NodeEvaluatorGenerator;

public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private final int maxVisitedNodes;
    public final NodeEvaluator nodeEvaluator;
    private static final boolean DEBUG = false;
    private final BinaryHeap openSet = new BinaryHeap();
    private final @Nullable NodeEvaluatorGenerator nodeEvaluatorGenerator; // Kaiiju - petal - we use this later to generate an evaluator

    public PathFinder(NodeEvaluator pathNodeMaker, int range, @Nullable NodeEvaluatorGenerator nodeEvaluatorGenerator) { // Kaiiju - petal - add nodeEvaluatorGenerator
        this.nodeEvaluator = pathNodeMaker;
        this.maxVisitedNodes = range;
        // Kaiiju start - petal - support nodeEvaluatorgenerators
        this.nodeEvaluatorGenerator = nodeEvaluatorGenerator;
    }

    public PathFinder(NodeEvaluator pathNodeMaker, int range) {
        this(pathNodeMaker, range, null);
        // Kaiiju end
    }

    @Nullable
    public Path findPath(PathNavigationRegion world, Mob mob, Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier) {
        if (!org.dreeam.leaf.config.modules.async.AsyncPathfinding.enabled)
            this.openSet.clear(); // Kaiiju - petal - it's always cleared in processPath
        // Kaiiju start - petal - use a generated evaluator if we have one otherwise run sync
        NodeEvaluator nodeEvaluator = this.nodeEvaluatorGenerator == null
                ? this.nodeEvaluator
                : NodeEvaluatorCache.takeNodeEvaluator(this.nodeEvaluatorGenerator, this.nodeEvaluator);
        nodeEvaluator.prepare(world, mob);
        Node node = nodeEvaluator.getStart();
        // Kaiiju end
        if (node == null) {
            NodeEvaluatorCache.removeNodeEvaluator(nodeEvaluator); // Kaiiju - petal - handle nodeEvaluatorGenerator
            return null;
        } else {
            // Paper start - Perf: remove streams and optimize collection
            List<Map.Entry<Target, BlockPos>> map = Lists.newArrayList();
            for (BlockPos pos : positions) {
                map.add(new java.util.AbstractMap.SimpleEntry<>(nodeEvaluator.getGoal(pos.getX(), pos.getY(), pos.getZ()), pos)); // Kaiiju - petal - handle nodeEvaluatorGenerator
            }
            // Paper end - Perf: remove streams and optimize collection
            // Kaiiju start - petal - async path processing
            if (this.nodeEvaluatorGenerator == null) {
                // run sync :(
                NodeEvaluatorCache.removeNodeEvaluator(nodeEvaluator);
                return this.findPath(node, map, followRange, distance, rangeMultiplier); // Gale - Purpur - remove vanilla profiler
            }

            return new AsyncPath(Lists.newArrayList(), positions, () -> {
                try {
                    return this.processPath(nodeEvaluator, node, map, followRange, distance, rangeMultiplier);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    nodeEvaluator.done();
                    NodeEvaluatorCache.returnNodeEvaluator(nodeEvaluator);
                }
            });
            // Kaiiju end
        }
    }

    @Nullable
    // Paper start - Perf: remove streams and optimize collection
    private Path findPath(Node startNode, List<Map.Entry<Target, BlockPos>> positions, float followRange, int distance, float rangeMultiplier) { // Gale - Purpur - remove vanilla profiler
        // Kaiiju start - petal - split pathfinding into the original sync method for compat and processing for delaying
        try {
            return this.processPath(this.nodeEvaluator, startNode, positions, followRange, distance, rangeMultiplier);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            this.nodeEvaluator.done();
        }
    }

    private synchronized @org.jetbrains.annotations.NotNull Path processPath(NodeEvaluator nodeEvaluator, Node startNode, List<Map.Entry<Target, BlockPos>> positions, float followRange, int distance, float rangeMultiplier) { // sync to only use the caching functions in this class on a single thread
        org.apache.commons.lang3.Validate.isTrue(!positions.isEmpty()); // ensure that we have at least one position, which means we'll always return a path
        // Kaiiju end
        // Set<Target> set = positions.keySet();
        startNode.g = 0.0F;
        startNode.h = this.getBestH(startNode, positions); // Paper - optimize collection
        startNode.f = startNode.h;
        this.openSet.clear();
        this.openSet.insert(startNode);
        // Set<Node> set2 = ImmutableSet.of(); // Paper - unused - diff on change
        int i = 0;
        List<Map.Entry<Target, BlockPos>> entryList = Lists.newArrayListWithExpectedSize(positions.size()); // Paper - optimize collection
        int j = (int)((float)this.maxVisitedNodes * rangeMultiplier);

        while(!this.openSet.isEmpty()) {
            ++i;
            if (i >= j) {
                break;
            }

            Node node = this.openSet.pop();
            node.closed = true;

            // Paper start - optimize collection
            for(int i1 = 0; i1 < positions.size(); i1++) {
                final Map.Entry<Target, BlockPos> entry = positions.get(i1);
                Target target = entry.getKey();
                if (node.distanceManhattan(target) <= (float)distance) {
                    target.setReached();
                    entryList.add(entry);
                    // Paper end - Perf: remove streams and optimize collection
                }
            }

            if (!entryList.isEmpty()) { // Paper - Perf: remove streams and optimize collection; rename
                break;
            }

            if (!(node.distanceTo(startNode) >= followRange)) {
                int k = nodeEvaluator.getNeighbors(this.neighbors, node); // Kaiiju - petal - use provided nodeEvaluator

                for(int l = 0; l < k; ++l) {
                    Node node2 = this.neighbors[l];
                    float f = this.distance(node, node2);
                    node2.walkedDistance = node.walkedDistance + f;
                    float g = node.g + f + node2.costMalus;
                    if (node2.walkedDistance < followRange && (!node2.inOpenSet() || g < node2.g)) {
                        node2.cameFrom = node;
                        node2.g = g;
                        node2.h = this.getBestH(node2, positions) * 1.5F; // Paper - Perf: remove streams and optimize collection
                        if (node2.inOpenSet()) {
                            this.openSet.changeCost(node2, node2.g + node2.h);
                        } else {
                            node2.f = node2.g + node2.h;
                            this.openSet.insert(node2);
                        }
                    }
                }
            }
        }

        // Paper start - Perf: remove streams and optimize collection
        Path best = null;
        boolean entryListIsEmpty = entryList.isEmpty();
        Comparator<Path> comparator = entryListIsEmpty ? Comparator.comparingInt(Path::getNodeCount)
            : Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount);
        for (Map.Entry<Target, BlockPos> entry : entryListIsEmpty ? positions : entryList) {
            Path path = this.reconstructPath(entry.getKey().getBestNode(), entry.getValue(), !entryListIsEmpty);
            if (best == null || comparator.compare(path, best) < 0)
                best = path;
        }
        //noinspection ConstantConditions // Kaiiju - petal - ignore this warning, we know that the above loop always runs at least once since positions is not empty
        return best;
        // Paper end - Perf: remove streams and optimize collection
    }

    protected float distance(Node a, Node b) {
        return a.distanceTo(b);
    }

    private float getBestH(Node node, List<Map.Entry<Target, BlockPos>> targets) { // Paper - Perf: remove streams and optimize collection; Set<Target> -> List<Map.Entry<Target, BlockPos>>
        float f = Float.MAX_VALUE;

        // Paper start - Perf: remove streams and optimize collection
        for (int i = 0, targetsSize = targets.size(); i < targetsSize; i++) {
            final Target target = targets.get(i).getKey();
            // Paper end - Perf: remove streams and optimize collection
            float g = node.distanceTo(target);
            target.updateBest(g, node);
            f = Math.min(g, f);
        }

        return f;
    }

    private Path reconstructPath(Node endNode, BlockPos target, boolean reachesTarget) {
        List<Node> list = Lists.newArrayList();
        Node node = endNode;
        list.add(0, endNode);

        while(node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }

        return new Path(list, target, reachesTarget);
    }
}
