package org.dreeam.leaf.config.modules.opt;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class CacheMinecartCollision implements IConfigModule {


    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "cache_minecart_collision";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = false;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("performance.cache_minecart_collision", "Cache the minecart collision result to prevent massive stacked minecart lag the server.");
    }
}
