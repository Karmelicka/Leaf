// Gale - Gale configuration

package org.galemc.gale.configuration;

import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.spongepowered.configurate.objectmapping.meta.PostProcess;
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

        // Gale start - Pufferfish - SIMD support
        public Simd simd;
        public class Simd extends ConfigurationPart {
            public boolean warnIfDisabled = true;
            public boolean logVectorSizesToConsole = false;
        }
        // Gale end - Pufferfish - SIMD support

    }

    public GameplayMechanics gameplayMechanics;
    public class GameplayMechanics extends ConfigurationPart {

        public boolean enableBookWriting = true; // Gale - Pufferfish - make book writing configurable

    }

    public Misc misc;
    public class Misc extends ConfigurationPart {

        public boolean verifyChatOrder = true; // Gale - Pufferfish - make chat order verification configurable

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

            // Gale start - include hardware specs in timings
            public HardwareSpecs hardwareSpecs;
            public class HardwareSpecs extends ConfigurationPart {
                public boolean cpu = true;
                public boolean disks = true;
                public boolean gpus = true;
                public boolean memory = true;
            }
            // Gale end - include hardware specs in timings

        }

    }

    public LogToConsole logToConsole;
    public class LogToConsole extends ConfigurationPart {

        public boolean invalidStatistics = true; // Gale - EMC - do not log invalid statistics
        public boolean ignoredAdvancements = true; // Gale - Purpur - do not log ignored advancements
        public boolean setBlockInFarChunk = true; // Gale - Purpur - do not log setBlock in far chunks
        public boolean unrecognizedRecipes = false; // Gale - Purpur - do not log unrecognized recipes
        public boolean legacyMaterialInitialization = false; // Gale - Purpur - do not log legacy Material initialization

        public Chat chat;
        public class Chat extends ConfigurationPart {
            public boolean emptyMessageWarning = false; // Gale - do not log empty message warnings
            public boolean expiredMessageWarning = false; // Gale - do not log expired message warnings
        }

        // Gale start - Purpur - do not log plugin library loads
        public PluginLibraryLoader pluginLibraryLoader;
        public class PluginLibraryLoader extends ConfigurationPart {

            public boolean downloads = true;
            public boolean startLoadLibrariesForPlugin = true;
            public boolean libraryLoaded = true;

            @PostProcess
            public void postProcess() {
                JavaPluginLoader.logDownloads = this.downloads;
                JavaPluginLoader.logStartLoadLibrariesForPlugin = this.startLoadLibrariesForPlugin;
                JavaPluginLoader.logLibraryLoaded = this.libraryLoaded;
            }

        }
        // Gale end - Purpur - do not log plugin library loads

    }

}
