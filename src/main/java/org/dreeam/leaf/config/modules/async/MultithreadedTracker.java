package org.dreeam.leaf.config.modules.async;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class MultithreadedTracker implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.ASYNC;
    }

    @Override
    public String getBaseName() {
        return "async_entity_tracker";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = false;
    @ConfigInfo(baseName = "max-threads")
    public static int asyncEntityTrackerMaxThreads = 0;
    @ConfigInfo(baseName = "keepalive")
    public static int asyncEntityTrackerKeepalive = 60;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("", """
                Whether or not async entity tracking should be enabled.
                You may encounter issues with NPCs""");

        if (asyncEntityTrackerMaxThreads < 0)
            asyncEntityTrackerMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() + asyncEntityTrackerMaxThreads, 1);
        else if (asyncEntityTrackerMaxThreads == 0)
            asyncEntityTrackerMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);
        if (!enabled)
            asyncEntityTrackerMaxThreads = 0;
        else
            MinecraftServer.LOGGER.info("Using {} threads for Async Entity Tracker", asyncEntityTrackerMaxThreads);
    }
}
