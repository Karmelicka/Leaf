// Gale - Gale configuration

package org.galemc.gale.configuration;

import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@SuppressWarnings({"CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal", "NotNullFieldNotInitialized", "InnerClassMayBeStatic"})
public class GaleGlobalConfiguration extends ConfigurationPart {
    static final int CURRENT_VERSION = 1;
    private static GaleGlobalConfiguration instance;
    public static GaleGlobalConfiguration get() {
        return instance;
    }
    static void set(GaleGlobalConfiguration instance) {
        GaleGlobalConfiguration.instance = instance;
    }

    @Setting(Configuration.VERSION_FIELD)
    public int version = CURRENT_VERSION;

    public SmallOptimizations smallOptimizations;
    public class SmallOptimizations extends ConfigurationPart {

        public int dummyValue = 0;

    }

    public Misc misc;
    public class Misc extends ConfigurationPart {

        public IncludeInTimingsReport includeInTimingsReport;
        public class IncludeInTimingsReport extends ConfigurationPart {
            
            // Gale start - include server.properties in timings
            public ServerProperties serverProperties;
            public class ServerProperties extends ConfigurationPart {
                public boolean dataPacks = true;
                public boolean enableRcon = false;
                public boolean generatorSettings = true;
                public boolean levelName = false;
                public boolean motd = false;
                public boolean queryPort = false;
                public boolean rconPort = false;
                public boolean resourcePackPrompt = false;
                @Setting("resource-pack-and-resource-pack-sha1")
                public boolean resourcePackAndResourcePackSha1 = false;
                public boolean serverIp = false;
                public boolean serverPort = false;
                public boolean textFilteringConfig = false;
            }
            // Gale end - include server.properties in timings

        }

    }

}
