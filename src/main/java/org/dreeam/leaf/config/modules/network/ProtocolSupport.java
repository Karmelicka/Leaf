package org.dreeam.leaf.config.modules.network;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

import java.util.concurrent.ThreadLocalRandom;

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

    @ConfigInfo(baseName = "chatImage-protocol")
    public static boolean chatImageProtocol = false;

    @ConfigInfo(baseName = "xaero-map-protocol")
    public static boolean xaeroMapProtocol = false;
    @ConfigInfo(baseName = "xaero-map-server-id")
    public static int xaeroMapServerID = ThreadLocalRandom.current().nextInt(); // Leaf - Faster Random

    @ConfigInfo(baseName = "syncmatica-enabled")
    public static boolean syncmaticaProtocol = false;
    @ConfigInfo(baseName = "syncmatica-quota")
    public static boolean syncmaticaQuota = false;
    @ConfigInfo(baseName = "syncmatica-quota-limit")
    public static int syncmaticaQuotaLimit = 40000000;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        if (syncmaticaProtocol) {
            top.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol.init();
        }
    }
}
