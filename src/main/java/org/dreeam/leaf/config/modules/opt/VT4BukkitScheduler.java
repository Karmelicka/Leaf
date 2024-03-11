package org.dreeam.leaf.config.modules.opt;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class VT4BukkitScheduler implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "use_virtual_thread_for_async_scheduler";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = false;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("performance.use_virtual_thread_for_async_scheduler", """
                 Use the new Virtual Thread introduced in JDK 21 for CraftAsyncScheduler.
                """);
    }
}
