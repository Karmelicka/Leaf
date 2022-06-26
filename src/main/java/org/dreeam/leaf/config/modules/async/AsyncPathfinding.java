package org.dreeam.leaf.config.modules.async;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class AsyncPathfinding implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.ASYNC;
    }

    @Override
    public String getBaseName() {
        return "async_pathfinding";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = false;
    @ConfigInfo(baseName = "max-threads")
    public static int asyncPathfindingMaxThreads = 0;
    @ConfigInfo(baseName = "keepalive")
    public static int asyncPathfindingKeepalive = 60;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        if (asyncPathfindingMaxThreads < 0)
            asyncPathfindingMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() + asyncPathfindingMaxThreads, 1);
        else if (asyncPathfindingMaxThreads == 0)
            asyncPathfindingMaxThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);
        if (!enabled)
            asyncPathfindingMaxThreads = 0;
        else
            MinecraftServer.LOGGER.info("Using {} threads for Async Pathfinding", asyncPathfindingMaxThreads);
    }
}
