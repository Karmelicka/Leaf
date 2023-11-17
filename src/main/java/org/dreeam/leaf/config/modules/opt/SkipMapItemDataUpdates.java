package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class SkipMapItemDataUpdates implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "skip_map_item_data_updates_if_map_does_not_have_craftmaprenderer";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
}
