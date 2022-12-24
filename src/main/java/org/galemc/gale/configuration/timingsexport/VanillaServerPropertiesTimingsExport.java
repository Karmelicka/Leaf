// Gale - include server.properties in timings

package org.galemc.gale.configuration.timingsexport;

import co.aikar.timings.TimingsExport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.galemc.gale.configuration.GaleGlobalConfiguration;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.util.Optional;

/**
 * Exports the vanilla server.properties to a JSON object, to be included in a timings report.
 *
 * @see TimingsExport
 *
 * @author Martijn Muijsers under GPL-3.0
 */
public final class VanillaServerPropertiesTimingsExport {

    private VanillaServerPropertiesTimingsExport() {}

    @SuppressWarnings("unchecked")
    public static @NotNull JSONObject get() {

        var json = new JSONObject();
        var properties = ((DedicatedServer) MinecraftServer.getServer()).getProperties();
        var includeConfig = GaleGlobalConfiguration.get().misc.includeInTimingsReport.serverProperties;

        json.put("allow-flight", String.valueOf(properties.allowFlight));
        json.put("allow-nether", String.valueOf(properties.allowNether));
        json.put("broadcast-console-to-ops", String.valueOf(properties.broadcastConsoleToOps));
        json.put("broadcast-rcon-to-ops", String.valueOf(properties.broadcastRconToOps));
        json.put("debug", String.valueOf(properties.debug));
        json.put("difficulty", String.valueOf(properties.difficulty));
        json.put("enable-command-block", String.valueOf(properties.enableCommandBlock));
        json.put("enable-jmx-monitoring", String.valueOf(properties.enableJmxMonitoring));
        json.put("enable-query", String.valueOf(properties.enableQuery));
        if (includeConfig.enableRcon) {
            json.put("enable-rcon", String.valueOf(properties.enableRcon));
        }
        json.put("enable-status", String.valueOf(properties.enableStatus));
        json.put("enforce-secure-profile", String.valueOf(properties.enforceSecureProfile));
        json.put("enforce-whitelist", String.valueOf(properties.enforceWhitelist));
        json.put("entity-broadcast-range-percentage", String.valueOf(properties.entityBroadcastRangePercentage));
        json.put("force-gamemode", String.valueOf(properties.forceGameMode));
        json.put("function-permission-level", String.valueOf(properties.functionPermissionLevel));
        json.put("gamemode", String.valueOf(properties.gamemode));
        Optional.ofNullable(properties.worldOptions).ifPresent(worldOptions -> json.put("generate-structures", String.valueOf(worldOptions.generateStructures())));
        if (includeConfig.generatorSettings) {
            Optional.ofNullable(properties.worldDimensionData).ifPresent(worldDimensionData -> json.put("generator-settings", String.valueOf(worldDimensionData.generatorSettings())));
        }
        json.put("hardcore", String.valueOf(properties.hardcore));
        json.put("hide-online-players", String.valueOf(properties.hideOnlinePlayers));
        if (includeConfig.dataPacks) {
            Optional.ofNullable(properties.initialDataPackConfiguration).ifPresent(initialDataPackConfiguration -> {
                json.put("initial-enabled-packs", String.valueOf(initialDataPackConfiguration.getEnabled()));
                json.put("initial-disabled-packs", String.valueOf(initialDataPackConfiguration.getDisabled()));
            });
        }
        if (includeConfig.levelName) {
            json.put("level-name", String.valueOf(properties.levelName));
        }
        // Note: level-seed is never included to prevent it being leaked
//        if (includeConfig.levelSeed) {
//            json.put("level-seed", String.valueOf(properties.levelSeed));
//        }
        Optional.ofNullable(properties.worldDimensionData).ifPresent(worldDimensionData -> json.put("level-type", String.valueOf(worldDimensionData.levelType())));
        json.put("log-ips", String.valueOf(properties.logIPs));
        json.put("max-chained-neighbor-updates", String.valueOf(properties.maxChainedNeighborUpdates));
        json.put("max-players", String.valueOf(properties.maxPlayers));
        json.put("max-tick-time", String.valueOf(properties.maxTickTime));
        json.put("max-world-size", String.valueOf(properties.maxWorldSize));
        if (includeConfig.motd) {
            json.put("motd", String.valueOf(properties.motd));
        }
        json.put("network-compression-threshold", String.valueOf(properties.networkCompressionThreshold));
        json.put("online-mode", String.valueOf(properties.onlineMode));
        json.put("op-permission-level", String.valueOf(properties.opPermissionLevel));
        Optional.ofNullable(properties.playerIdleTimeout).ifPresent(playerIdleTimeout -> json.put("player-idle-timeout", String.valueOf(playerIdleTimeout.get())));
        json.put("prevent-proxy-connections", String.valueOf(properties.preventProxyConnections));
        json.put("pvp", String.valueOf(properties.pvp));
        if (includeConfig.queryPort) {
            json.put("query-port", String.valueOf(properties.queryPort));
        }
        json.put("rate-limit", String.valueOf(properties.rateLimitPacketsPerSecond));
        // Note: rcon-password is never included to prevent it being leaked
//        if (includeConfig.rconPassword) {
//            json.put("rcon-password", String.valueOf(properties.rconPassword));
//        }
        if (includeConfig.rconPort) {
            json.put("rcon-port", String.valueOf(properties.queryPort));
        }
        properties.serverResourcePackInfo.ifPresent(serverResourcePackInfo -> {
            json.put("require-resource-pack", String.valueOf(serverResourcePackInfo.isRequired()));
            if (includeConfig.resourcePackAndResourcePackSha1) {
                json.put("resource-pack", String.valueOf(serverResourcePackInfo.url()));
                json.put("resource-pack-sha1", String.valueOf(serverResourcePackInfo.hash()));
            }
        });
        if (includeConfig.resourcePackPrompt) {
            json.put("resource-pack-prompt", String.valueOf(properties.resourcePackPrompt));
        }
        if (includeConfig.serverIp) {
            json.put("server-ip", String.valueOf(properties.serverIp));
        }
        if (includeConfig.serverPort) {
            json.put("server-port", String.valueOf(properties.serverPort));
        }
        json.put("simulation-distance", String.valueOf(properties.simulationDistance));
        json.put("spawn-animals", String.valueOf(properties.spawnAnimals));
        json.put("spawn-monsters", String.valueOf(properties.spawnMonsters));
        json.put("spawn-npcs", String.valueOf(properties.spawnNpcs));
        json.put("spawn-protection", String.valueOf(properties.spawnProtection));
        json.put("sync-chunk-writes", String.valueOf(properties.syncChunkWrites));
        if (includeConfig.textFilteringConfig) {
            json.put("text-filtering-config", String.valueOf(properties.textFilteringConfig));
        }
        json.put("use-native-transport", String.valueOf(properties.useNativeTransport));
        json.put("view-distance", String.valueOf(properties.viewDistance));
        Optional.ofNullable(properties.whiteList).ifPresent(whiteList -> json.put("white-list", String.valueOf(whiteList.get())));

        return json;

    }

}
