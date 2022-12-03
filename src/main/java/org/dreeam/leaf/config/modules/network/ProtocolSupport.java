package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class ProtocolSupport implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.NETWORK;
    }

    @Override
    public String getBaseName() {
        return "protocol_support";
    }

    @ConfigInfo(baseName = "jade-protocol")
    public static boolean jadeProtocol = false;
}
