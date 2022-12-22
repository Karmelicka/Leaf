package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.ProfilerFiller;

public interface PreparableReloadListener {
    // Gale start - Purpur - remove vanilla profiler
    default CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier synchronizer, ResourceManager manager, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
        return this.reload(synchronizer, manager, prepareExecutor, applyExecutor);
    }
    
    CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor);
    // Gale end - Purpur - remove vanilla profiler

    default String getName() {
        return this.getClass().getSimpleName();
    }

    public interface PreparationBarrier {
        <T> CompletableFuture<T> wait(T preparedObject);
    }
}
