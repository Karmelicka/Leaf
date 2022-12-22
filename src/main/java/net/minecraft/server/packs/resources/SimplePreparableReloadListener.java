package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor) { // Gale - Purpur - remove vanilla profiler
        return CompletableFuture.supplyAsync(() -> {
            return this.prepare(manager, InactiveProfiler.INSTANCE); // Gale - Purpur - remove vanilla profiler
        }, prepareExecutor).thenCompose(synchronizer::wait).thenAcceptAsync((prepared) -> {
            this.apply(prepared, manager, InactiveProfiler.INSTANCE); // Gale - Purpur - remove vanilla profiler
        }, applyExecutor);
    }

    protected abstract T prepare(ResourceManager manager, ProfilerFiller profiler);

    protected abstract void apply(T prepared, ResourceManager manager, ProfilerFiller profiler);
}
