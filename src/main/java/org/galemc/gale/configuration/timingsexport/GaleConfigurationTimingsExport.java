// Gale - Gale configuration

package org.galemc.gale.configuration.timingsexport;

import co.aikar.timings.TimingsExport;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

public final class GaleConfigurationTimingsExport {

    private GaleConfigurationTimingsExport() {}

    public static @NotNull JSONObject get() {
        var json = TimingsExport.mapAsJSON(Bukkit.spigot().getGaleConfig(), null);
        return json;
    }

}
