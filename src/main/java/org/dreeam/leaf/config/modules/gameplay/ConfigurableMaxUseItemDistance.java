package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class ConfigurableMaxUseItemDistance implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.GAMEPLAY;
    }

    @Override
    public String getBaseName() {
        return "configurable_max_use_item_distance";
    }

    @ConfigInfo(baseName = "max-use-item-distance", comments = "The max distance of UseItem for players")
    public static double maxUseItemDistance = 1.0000001D;
    @ConfigInfo(baseName = "remove-max-distance-check", comments = """
            To enable this, players can use some packet modules
            with hack clients and the NoCom Exploit!!""")
    public static boolean removeUseItemOnPacketTooFarCheck = false;
}
