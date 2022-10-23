package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class UseSpigotItemMergingMech implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.GAMEPLAY;
    }

    @Override
    public String getBaseName() {
        return "use-spigot-item-merging-mechanism";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
}
