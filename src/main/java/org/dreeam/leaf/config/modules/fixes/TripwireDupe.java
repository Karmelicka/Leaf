package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class TripwireDupe implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.FIXES;
    }

    @Override
    public String getBaseName() {
        return "fix_tripwire_dupe";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
}
