package org.dreeam.leaf.config.legacy.upgrader;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;
import org.dreeam.leaf.config.LeafConfig;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class V1ToV2 implements IConfigModule {

    private static final YamlFile legacyConfig = new YamlFile();
    private static final File legacyConfigFile = new File("leaf.yml");

    private static Object version, serverModName, serverGuiName, removeMojangUsernameCheck, removeSpigotCheckBungeeConfig, removeUseItemOnPacketTooFar,
            maxUseItemDistance, disableMovedWronglyThreshold, enableAsyncMobSpawning, dabEnabled, startDistance, maximumActivationPrio, activationDistanceMod,
            blackedEntities, throttleInactiveGoalSelectorTick, useSpigotItemMergingMechanism, optimizedPoweredRails, asyncPathfinding, asyncPathfindingMaxThreads,
            asyncPathfindingKeepalive, asyncEntityTracker, asyncEntityTrackerMaxThreads, asyncEntityTrackerKeepalive, cacheMinecartCollision,
            skipMapItemDataUpdatesIfMapDoesNotHaveCraftMapRenderer, jadeProtocol, appleskinProtocol, xaeroMapProtocol, xaeroMapServerID, syncmaticaProtocol, syncmaticaQuota,
            syncmaticaQuotaLimit, sentryDsn, useVanillaEndTeleport, snowballCanKnockback, eggCanKnockback, fixTripwireDupe, including5sIngetTPS;
    private static Map<String, Object> ttls = new ConcurrentHashMap<>();

    private static void initializeKeys() {
        version = getKey("info.version");
        serverModName = getKey("server-mod-name");
        serverGuiName = getKey("server-Gui-name");
        removeMojangUsernameCheck = getKey("remove-Mojang-username-check");
        removeSpigotCheckBungeeConfig = getKey("remove-Spigot-check-bungee-config");
        removeUseItemOnPacketTooFar = getKey("remove-UseItemOnPacket-too-far-check");
        maxUseItemDistance = getKey("max-UseItem-distance");
        disableMovedWronglyThreshold = getKey("disable-MovedWronglyThreshold");
        enableAsyncMobSpawning = getKey("performance.enable-async-mob-spawning");
        dabEnabled = getKey("performance.dab.enabled", "dab.enabled");
        startDistance = getKey("performance.dab.start-distance", "dab.start-distance");
        maximumActivationPrio = getKey("performance.dab.max-tick-freq", "dab.max-tick-freq");
        activationDistanceMod = getKey("performance.dab.activation-dist-mod", "dab.activation-dist-mod");
        blackedEntities = getKey("performance.dab.blacklisted-entities", "dab.blacklisted-entities");
        throttleInactiveGoalSelectorTick = getKey("performance.inactive-goal-selector-throttle", "inactive-goal-selector-throttle");
        legacyConfig.getMapValues(true).forEach((key, ttl) -> {
            if (key.startsWith("performance.entity_timeouts.")) {
                String e = key.replaceAll("performance.entity_timeouts.", "");
                ttls.putIfAbsent(e, ttl);
            }
        });
        useSpigotItemMergingMechanism = getKey("performance.use-spigot-item-merging-mechanism");
        optimizedPoweredRails = getKey("performance.optimizedPoweredRails");
        asyncPathfinding = getKey("performance.async-pathfinding.enable");
        asyncPathfindingMaxThreads = getKey("performance.async-pathfinding.max-threads");
        asyncPathfindingKeepalive = getKey("performance.async-pathfinding.keepalive");
        asyncEntityTracker = getKey("performance.async-entity-tracker.enable");
        asyncEntityTrackerMaxThreads = getKey("performance.async-entity-tracker.max-threads");
        asyncEntityTrackerKeepalive = getKey("performance.async-entity-tracker.keepalive");
        cacheMinecartCollision = getKey("performance.cache-minecart-collision");
        skipMapItemDataUpdatesIfMapDoesNotHaveCraftMapRenderer = getKey("performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer");
        jadeProtocol = getKey("network.protocol.jade-protocol");
        appleskinProtocol = getKey("network.protocol.appleskin-protocol");
        xaeroMapProtocol = getKey("network.protocol.xaero-map-protocol");
        xaeroMapServerID = getKey("network.protocol.xaero-map-server-id");
        syncmaticaProtocol = getKey("network.protocol.syncmatica.enable");
        syncmaticaQuota = getKey("network.protocol.syncmatica.quota");
        syncmaticaQuotaLimit = getKey("network.protocol.syncmatica.quota-limit");
        sentryDsn = getKey("sentry-dsn", "performance.sentry-dsn");
        useVanillaEndTeleport = getKey("use-vanilla-end-teleport");
        snowballCanKnockback = getKey("playerKnockback.snowball-knockback-players");
        eggCanKnockback = getKey("playerKnockback.egg-knockback-players");
        fixTripwireDupe = getKey("gameplay.fix-tripwire-dupe");
        including5sIngetTPS = getKey("including-5s-in-getTPS");
    }

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "converter";
    }

    @Override
    public void onLoaded(CommentedFileConfig config) {
        loadLegacyConfig();

        LeafConfig.logger.info("Converting config from v{} to v2.0 format...", version);

        updateConfig("misc.rebrand.server-mod-name", serverModName, config);
        updateConfig("misc.rebrand.server-gui-name", serverGuiName, config);
        updateConfig("misc.remove_vanilla_username_check.enabled", removeMojangUsernameCheck, config);
        updateConfig("misc.remove_spigot_check_bungee_config.enabled", removeSpigotCheckBungeeConfig, config);
        updateConfig("misc.configurable_max_use_item_distance.remove-max-distance-check", removeUseItemOnPacketTooFar, config);
        updateConfig("gameplay.configurable_max_use_item_distance.max-use-item-distance", maxUseItemDistance, config);
        updateConfig("gameplay.disable_moved_wrongly_threshold.enabled", disableMovedWronglyThreshold, config);
        updateConfig("async.async_mob_spawning.enabled", enableAsyncMobSpawning, config);
        updateConfig("performance.dab.enabled", dabEnabled, config);
        updateConfig("performance.dab.start-distance", startDistance, config);
        updateConfig("performance.dab.max-tick-freq", maximumActivationPrio, config);
        updateConfig("performance.dab.activation-dist-mod", activationDistanceMod, config);
        updateConfig("performance.dab.blacklisted-entities", blackedEntities, config);
        updateConfig("performance.inactive_goal_selector_throttle.enabled", throttleInactiveGoalSelectorTick, config);
        ttls.forEach((e, ttl) -> updateConfig("performance.entity_timeouts." + e, ttl, config));
        updateConfig("gameplay.use-spigot-item-merging-mechanism.enabled", useSpigotItemMergingMechanism, config);
        updateConfig("performance.optimized_powered_rails.enabled", optimizedPoweredRails, config);
        updateConfig("async.async_pathfinding.enabled", asyncPathfinding, config);
        updateConfig("async.async_pathfinding.max-threads", asyncPathfindingMaxThreads, config);
        updateConfig("async.async_pathfinding.keepalive", asyncPathfindingKeepalive, config);
        updateConfig("async.async_entity_tracker.enabled", asyncEntityTracker, config);
        updateConfig("async.async_entity_tracker.max-threads", asyncEntityTrackerMaxThreads, config);
        updateConfig("async.async_entity_tracker.keepalive", asyncEntityTrackerKeepalive, config);
        updateConfig("performance.cache_minecart_collision.enabled", cacheMinecartCollision, config);
        updateConfig("performance.skip_map_item_data_updates_if_map_does_not_have_craftmaprenderer.enabled", skipMapItemDataUpdatesIfMapDoesNotHaveCraftMapRenderer, config);
        updateConfig("network.protocol_support.jade-protocol", jadeProtocol, config);
        updateConfig("network.protocol_support.appleskin-protocol", appleskinProtocol, config);
        updateConfig("network.protocol_support.xaero-map-protocol", xaeroMapProtocol, config);
        updateConfig("network.protocol_support.xaero-map-server-id", xaeroMapServerID, config);
        updateConfig("network.protocol_support.syncmatica-enabled", syncmaticaProtocol, config);
        updateConfig("network.protocol_support.syncmatica-quota", syncmaticaQuota, config);
        updateConfig("network.protocol_support.syncmatica-quota-limit", syncmaticaQuotaLimit, config);
        updateConfig("misc.sentry_dsn.sentry-dsn", sentryDsn, config);
        updateConfig("gameplay.use_vanilla_end_teleport.enabled", useVanillaEndTeleport, config);
        updateConfig("gameplay.knockback.snowball-knockback-players", snowballCanKnockback, config);
        updateConfig("gameplay.knockback.egg-knockback-players", eggCanKnockback, config);
        updateConfig("fixes.fix_tripwire_dupe.enabled", fixTripwireDupe, config);
        updateConfig("misc.including_5s_in_get_tps.enabled", including5sIngetTPS, config);

        config.save();

        File backupFolder = new File(LeafConfig.baseConfigFolder + "/legacy_backup");

        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        try {
            Files.move(Path.of(legacyConfigFile.getPath()), Path.of(LeafConfig.baseConfigFolder + "/legacy_backup/leaf_v" + version + "_backup.yml"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LeafConfig.logger.error("Config upgraded failed!");
            return;
        }

        LeafConfig.logger.info("Config upgraded successfully!, elapsed {}ms.", (System.nanoTime() - LeafConfig.beginTime) / 1000000);
    }

    private static void loadLegacyConfig() {
        try {
            legacyConfig.load(legacyConfigFile);
            initializeKeys();
        } catch (IOException e) {
            LeafConfig.logger.error("Failed to load legacy config! Config upgraded failed!");
        }
    }

    private static Object getKey(String key) {
        return legacyConfig.contains(key) ? legacyConfig.get(key) : null;
    }

    // In v1.x config, the old key still exists after converting to new key
    // So check the new key whether exists first, then check the old key.
    private static Object getKey(String key, String oldKey) {
        return legacyConfig.contains(key) ? legacyConfig.get(key) :
                legacyConfig.contains(oldKey) ? legacyConfig.get(oldKey) :
                        null;
    }

    // TOML doesn't allow null key value, so do a null check.
    private static void updateConfig(String key, Object value, CommentedFileConfig config) {
        if (value != null) config.set(key, value);
    }
}
