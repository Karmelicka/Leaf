package com.destroystokyo.paper;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.galemc.gale.version.AbstractPaperVersionFetcher;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.util.stream.StreamSupport;

// Gale start - branding changes - version fetcher
/**
 * The original version fetcher for Paper. Most of the original content of this class has been moved to
 * {@link AbstractPaperVersionFetcher}.
 */
public class PaperVersionFetcher extends AbstractPaperVersionFetcher {

    public PaperVersionFetcher() {
        super("master", "https://papermc.io/downloads/paper", "Paper", "PaperMC", "PaperMC", "Paper");
    }

    @Override
    protected boolean canFetchDistanceFromSiteApi() {
        return true;
    }

    @Override
    protected int fetchDistanceFromSiteApi(int jenkinsBuild) {
        return fetchDistanceFromSiteApi(jenkinsBuild, this.getMinecraftVersion());
    }
    // Gale end - branding changes - version fetcher

    private static int fetchDistanceFromSiteApi(int jenkinsBuild, @Nullable String siteApiVersion) {
        if (siteApiVersion == null) { return -1; }
        try {
            try (BufferedReader reader = Resources.asCharSource(
                new URL("https://api.papermc.io/v2/projects/paper/versions/" + siteApiVersion),
                Charsets.UTF_8
            ).openBufferedStream()) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                JsonArray builds = json.getAsJsonArray("builds");
                int latest = StreamSupport.stream(builds.spliterator(), false)
                    .mapToInt(e -> e.getAsInt())
                    .max()
                    .getAsInt();
                return latest - jenkinsBuild;
            } catch (JsonSyntaxException ex) {
                ex.printStackTrace();
                return -1;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

}
