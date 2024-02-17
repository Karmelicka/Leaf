package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class OptimizedPoweredRails implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "optimized_powered_rails";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
}
