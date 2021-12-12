package org.dreeam.leaf.config.modules.opt;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class ThrottleInactiveGoalSelectorTick implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "inactive_goal_selector_throttle";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("performance.inactive_goal_selector_throttle", """
                Throttles the AI goal selector in entity inactive ticks.
                This can improve performance by a few percent, but has minor gameplay implications.""");
    }
}
