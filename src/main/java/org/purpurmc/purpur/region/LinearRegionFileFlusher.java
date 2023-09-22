package org.purpurmc.purpur.region;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Queue;
import java.util.concurrent.*;
import org.purpurmc.purpur.PurpurConfig;
import org.bukkit.Bukkit;

public class LinearRegionFileFlusher {
    private final Queue<LinearRegionFile> savingQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("linear-flush-scheduler")
            .build()
    );
    private final ExecutorService executor = Executors.newFixedThreadPool(
        PurpurConfig.linearFlushThreads,
        new ThreadFactoryBuilder()
            .setNameFormat("linear-flusher-%d")
            .build()
    );

    public LinearRegionFileFlusher() {
        Bukkit.getLogger().info("Using " + PurpurConfig.linearFlushThreads + " threads for linear region flushing.");
        scheduler.scheduleAtFixedRate(this::pollAndFlush, 0L, PurpurConfig.linearFlushFrequency, TimeUnit.SECONDS);
    }

    public void scheduleSave(LinearRegionFile regionFile) {
        if (savingQueue.contains(regionFile)) return;
        savingQueue.add(regionFile);
    }

    private void pollAndFlush() {
        while (!savingQueue.isEmpty()) {
            LinearRegionFile regionFile = savingQueue.poll();
            if (!regionFile.closed && regionFile.isMarkedToSave())
                executor.execute(regionFile::flushWrapper);
        }
    }

    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }
}
