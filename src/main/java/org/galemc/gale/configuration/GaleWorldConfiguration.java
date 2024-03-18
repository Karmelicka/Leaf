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

        public boolean saveFireworks = true; // Gale - EMC - make saving fireworks configurable

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
            public int villagerItemRepickup = 100; // Gale - EMC - reduce villager item re-pickup

            public CheckNearbyItem checkNearbyItem;
            public class CheckNearbyItem extends ConfigurationPart {

                // Gale start - EMC - reduce hopper item checks
                public Hopper hopper;
                public class Hopper extends ConfigurationPart {

                    public int interval = 20;

                    public Minecart minecart;
                    public class Minecart extends ConfigurationPart {

                        public int interval = 20;

                        public TemporaryImmunity temporaryImmunity;
                        public class TemporaryImmunity extends ConfigurationPart {
                            public int duration = 100;
                            public int nearbyItemMaxAge = 1200;
                            public int checkForMinecartNearItemInterval = 20;
                            public boolean checkForMinecartNearItemWhileInactive = true;
                            public double maxItemHorizontalDistance = 24.0;
                            public double maxItemVerticalDistance = 4.0;
                        }

                    }

                }
                // Gale end - EMC - reduce hopper item checks

            }

        }

    }

    public GameplayMechanics gameplayMechanics;
    public class GameplayMechanics extends ConfigurationPart {

        public Fixes fixes;
        public class Fixes extends ConfigurationPart {
            public boolean tripwireDuping = true; // Gale - Leaf - make tripwire duping fix configurable
            public boolean keepMooshroomRotationAfterShearing = true; // Gale - Purpur - fix cow rotation when shearing mooshroom

            // Gale start - Purpur - fix MC-238526
            @Setting("mc-238526")
            public boolean mc238526 = false;
            // Gale end - Purpur - fix MC-238526

            // Gale start - Purpur - fix MC-121706
            @Setting("mc-121706")
            public boolean mc121706 = false;
            // Gale end - Purpur - fix MC-121706

            // Gale start - Mirai - fix MC-110386
            @Setting("mc-110386")
            public boolean mc110386 = true;
            // Gale end - Mirai - fix MC-110386

            // Gale start - Mirai - fix MC-31819
            @Setting("mc-31819")
            public boolean mc31819 = true;
            // Gale end - Mirai - fix MC-31819

        }

        public double entityWakeUpDurationRatioStandardDeviation = 0.2; // Gale - variable entity wake-up duration
        public boolean tryRespawnEnderDragonAfterEndCrystalPlace = true; // Gale - Pufferfish - make ender dragon respawn attempt after placing end crystals configurable

    }

}
