package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class ServerBrand implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "rebrand";
    }

    @ConfigInfo(baseName = "server-mod-name")
    public static String serverModName = "Leaf";

    @ConfigInfo(baseName = "server-gui-name")
    public static String serverGUIName = "Leaf Console";
}
