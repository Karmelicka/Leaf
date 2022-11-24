// Gale - Gale configuration

package org.galemc.gale.configuration;

import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import io.papermc.paper.configuration.PaperConfigurations;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "NotNullFieldNotInitialized", "InnerClassMayBeStatic"})
public class GaleWorldConfiguration extends ConfigurationPart {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int CURRENT_VERSION = 1;

    private transient final SpigotWorldConfig spigotConfig;
    private transient final ResourceLocation worldKey;
    public GaleWorldConfiguration(SpigotWorldConfig spigotConfig, ResourceLocation worldKey) {
        this.spigotConfig = spigotConfig;
        this.worldKey = worldKey;
    }

    public boolean isDefault() {
        return this.worldKey.equals(PaperConfigurations.WORLD_DEFAULTS_KEY);
    }

    @Setting(Configuration.VERSION_FIELD)
    public int version = CURRENT_VERSION;

    public SmallOptimizations smallOptimizations;
    public class SmallOptimizations extends ConfigurationPart {

        // Gale start - Airplane - reduce projectile chunk loading
        public MaxProjectileChunkLoads maxProjectileChunkLoads;
        public class MaxProjectileChunkLoads extends ConfigurationPart {

            public int perTick = 10;

            public PerProjectile perProjectile;
            public class PerProjectile extends ConfigurationPart {
                public int max = 10;
                public boolean resetMovementAfterReachLimit = false;
                public boolean removeFromWorldAfterReachLimit = false;
            }

        }
        // Gale end - Airplane - reduce projectile chunk loading

        public ReducedIntervals reducedIntervals;
        public class ReducedIntervals extends ConfigurationPart {

            public int acquirePoiForStuckEntity = 60; // Gale - Airplane - reduce acquire POI for stuck entities
            public int checkStuckInWall = 10; // Gale - Pufferfish - reduce in wall checks

        }

    }

}
