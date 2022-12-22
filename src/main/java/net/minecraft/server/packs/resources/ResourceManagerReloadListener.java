package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.Unit;

public interface ResourceManagerReloadListener extends PreparableReloadListener {
    @Override
    default CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor) { // Gale - Purpur - remove vanilla profiler
        return synchronizer.wait(Unit.INSTANCE).thenRunAsync(() -> {
            this.onResourceManagerReload(manager);
        }, applyExecutor);
    }

    void onResourceManagerReload(ResourceManager manager);
}
