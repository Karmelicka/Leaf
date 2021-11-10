package org.dreeam.leaf.config.modules.async;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.DoNotLoad;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class AsyncMobSpawning implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.ASYNC;
    }

    @Override
    public String getBaseName() {
        return "async_mob_spawning";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
    @DoNotLoad
    public static boolean asyncMobSpawningInitialized;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("async.async_mob_spawning", """
                Whether or not asynchronous mob spawning should be enabled.
                On servers with many entities, this can improve performance by up to 15%. You must have
                paper's per-player-mob-spawns setting set to true for this to work.
                One quick note - this does not actually spawn mobs async (that would be very unsafe).
                This just offloads some expensive calculations that are required for mob spawning.""");

        // This prevents us from changing the value during a reload.
        if (!asyncMobSpawningInitialized) {
            asyncMobSpawningInitialized = true;
            this.get("async.async_mob_spawning.enabled", enabled, config);
        }
    }
}
