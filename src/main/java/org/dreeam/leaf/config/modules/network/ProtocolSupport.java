package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

import java.util.Random;

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

    @ConfigInfo(baseName = "appleskin-protocol")
    public static boolean appleskinProtocol = false;

    @ConfigInfo(baseName = "xaero-map-protocol")
    public static boolean xaeroMapProtocol = false;
    @ConfigInfo(baseName = "xaero-map-server-id")
    public static int xaeroMapServerID = new Random().nextInt();
}
