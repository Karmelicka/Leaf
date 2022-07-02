package org.dreeam.leaf.async.tracker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.papermc.paper.util.maplist.IteratorSafeOrderedReferenceSet;
import io.papermc.paper.world.ChunkEntitySlices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MultithreadedTracker {

    private enum TrackerStage {
        UPDATE_PLAYERS,
        SEND_CHANGES
    }

    private static final Executor trackerExecutor = new ThreadPoolExecutor(
            1,
            org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerMaxThreads,
            org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerKeepalive, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder()
                    .setNameFormat("petal-async-tracker-thread-%d")
                    .setPriority(Thread.NORM_PRIORITY - 2)
                    .build());

    private final IteratorSafeOrderedReferenceSet<LevelChunk> entityTickingChunks;
    private final AtomicInteger taskIndex = new AtomicInteger();

    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks;
    private final AtomicInteger finishedTasks = new AtomicInteger();

    public MultithreadedTracker(IteratorSafeOrderedReferenceSet<LevelChunk> entityTickingChunks, ConcurrentLinkedQueue<Runnable> mainThreadTasks) {
        this.entityTickingChunks = entityTickingChunks;
        this.mainThreadTasks = mainThreadTasks;
    }

    public void processTrackQueue() {
        int iterator = this.entityTickingChunks.createRawIterator();

        if (iterator == -1) {
            return;
        }

        // start with updating players
        try {
            this.taskIndex.set(iterator);
            this.finishedTasks.set(0);

            for (int i = 0; i < org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerMaxThreads; i++) {
                trackerExecutor.execute(this::runUpdatePlayers);
            }

            while (this.taskIndex.get() < this.entityTickingChunks.getListSize()) {
                this.runMainThreadTasks();
                this.handleChunkUpdates(5); // assist
            }

            while (this.finishedTasks.get() != org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerMaxThreads) {
                this.runMainThreadTasks();
            }

            this.runMainThreadTasks(); // finish any remaining tasks
        } finally {
            this.entityTickingChunks.finishRawIterator();
        }

        // then send changes
        iterator = this.entityTickingChunks.createRawIterator();

        if (iterator == -1) {
            return;
        }

        try {
            do {
                LevelChunk chunk = this.entityTickingChunks.rawGet(iterator);

                if (chunk != null) {
                    this.updateChunkEntities(chunk, TrackerStage.SEND_CHANGES);
                }
            } while (++iterator < this.entityTickingChunks.getListSize());
        } finally {
            this.entityTickingChunks.finishRawIterator();
        }
    }

    private void runMainThreadTasks() {
        try {
            Runnable task;
            while ((task = this.mainThreadTasks.poll()) != null) {
                task.run();
            }
        } catch (Throwable throwable) {
            MinecraftServer.LOGGER.warn("Tasks failed while ticking track queue", throwable);
        }
    }

    private void runUpdatePlayers() {
        try {
            while (handleChunkUpdates(10));
        } finally {
            this.finishedTasks.incrementAndGet();
        }
    }

    private boolean handleChunkUpdates(int tasks) {
        int index = this.taskIndex.getAndAdd(tasks);

        for (int i = index; i < index + tasks && i < this.entityTickingChunks.getListSize(); i++) {
            LevelChunk chunk = this.entityTickingChunks.rawGet(i);
            if (chunk != null) {
                try {
                    this.updateChunkEntities(chunk, TrackerStage.UPDATE_PLAYERS);
                } catch (Throwable throwable) {
                    MinecraftServer.LOGGER.warn("Ticking tracker failed", throwable);
                }

            }
        }

        return index < this.entityTickingChunks.getListSize();
    }

    private void updateChunkEntities(LevelChunk chunk, TrackerStage trackerStage) {
        final ChunkEntitySlices entitySlices = chunk.level.getEntityLookup().getChunk(chunk.locX, chunk.locZ);
        if (entitySlices == null) {
            return;
        }

        final Entity[] rawEntities = entitySlices.entities.getRawData();
        final ChunkMap chunkMap = chunk.level.chunkSource.chunkMap;

        for (Entity entity : rawEntities) {
            if (entity != null) {
                ChunkMap.TrackedEntity entityTracker = chunkMap.entityMap.get(entity.getId());
                if (entityTracker != null) {
                    if (trackerStage == TrackerStage.SEND_CHANGES) {
                        entityTracker.serverEntity.sendChanges();
                    } else if (trackerStage == TrackerStage.UPDATE_PLAYERS) {
                        entityTracker.updatePlayers(entityTracker.entity.getPlayersInTrackRange());
                    }
                }
            }
        }
    }

}