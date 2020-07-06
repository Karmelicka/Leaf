package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class GateBehavior<E extends LivingEntity> implements BehaviorControl<E> {
    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final GateBehavior.OrderPolicy orderPolicy;
    private final GateBehavior.RunningPolicy runningPolicy;
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<>(false); // Paper - Fix Concurrency issue in ShufflingList during worldgen
    private Behavior.Status status = Behavior.Status.STOPPED;

    public GateBehavior(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryState, Set<MemoryModuleType<?>> memoriesToForgetWhenStopped, GateBehavior.OrderPolicy order, GateBehavior.RunningPolicy runMode, List<Pair<? extends BehaviorControl<? super E>, Integer>> tasks) {
        this.entryCondition = requiredMemoryState;
        this.exitErasedMemories = memoriesToForgetWhenStopped;
        this.orderPolicy = order;
        this.runningPolicy = runMode;
        tasks.forEach((task) -> {
            this.behaviors.add(task.getFirst(), task.getSecond());
        });
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    private boolean hasRequiredMemories(E entity) {
        for(Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            MemoryStatus memoryStatus = entry.getValue();
            if (!entity.getBrain().checkMemory(memoryModuleType, memoryStatus)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public final boolean tryStart(ServerLevel world, E entity, long time) {
        if (this.hasRequiredMemories(entity)) {
            this.status = Behavior.Status.RUNNING;
            this.orderPolicy.apply(this.behaviors);
            this.runningPolicy.apply(this.behaviors.stream(), world, entity, time);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void tickOrStop(ServerLevel world, E entity, long time) {
        this.behaviors.stream().filter((task) -> {
            return task.getStatus() == Behavior.Status.RUNNING;
        }).forEach((task) -> {
            task.tickOrStop(world, entity, time);
        });
        if (this.behaviors.stream().noneMatch((task) -> {
            return task.getStatus() == Behavior.Status.RUNNING;
        })) {
            this.doStop(world, entity, time);
        }

    }

    @Override
    public final void doStop(ServerLevel world, E entity, long time) {
        this.status = Behavior.Status.STOPPED;
        this.behaviors.stream().filter((task) -> {
            return task.getStatus() == Behavior.Status.RUNNING;
        }).forEach((task) -> {
            task.doStop(world, entity, time);
        });
        this.exitErasedMemories.forEach(entity.getBrain()::eraseMemory);
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = this.behaviors.stream().filter((task) -> {
            return task.getStatus() == Behavior.Status.RUNNING;
        }).collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }

    public static enum OrderPolicy {
        ORDERED((list) -> {
        }),
        SHUFFLED(ShufflingList::shuffle);

        private final Consumer<ShufflingList<?>> consumer;

        private OrderPolicy(Consumer<ShufflingList<?>> listModifier) {
            this.consumer = listModifier;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public static enum RunningPolicy {
        RUN_ONE {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> tasks, ServerLevel world, E entity, long time) {
                tasks.filter((task) -> {
                    return task.getStatus() == Behavior.Status.STOPPED;
                }).filter((task) -> {
                    return task.tryStart(world, entity, time);
                }).findFirst();
            }
        },
        TRY_ALL {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> tasks, ServerLevel world, E entity, long time) {
                tasks.filter((task) -> {
                    return task.getStatus() == Behavior.Status.STOPPED;
                }).forEach((task) -> {
                    task.tryStart(world, entity, time);
                });
            }
        };

        public abstract <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> tasks, ServerLevel world, E entity, long time);
    }
}
