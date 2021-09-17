// Gale - branding changes - version fetcher

package org.galemc.gale.version;

import com.destroystokyo.paper.PaperVersionFetcher;
import com.destroystokyo.paper.VersionHistoryManager;
import com.destroystokyo.paper.util.VersionFetcher;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An abstract version fetcher, derived from {@link PaperVersionFetcher}.
 * This class was then made to be a superclass of both {@link PaperVersionFetcher}
 * and {@link GaleVersionFetcher}.
 * <br>
 * Changes to {@link PaperVersionFetcher} are indicated by Gale marker comments.
 */
public abstract class AbstractPaperVersionFetcher implements VersionFetcher {
    private static final java.util.regex.Pattern VER_PATTERN = java.util.regex.Pattern.compile("^([0-9\\.]*)\\-.*R"); // R is an anchor, will always give '-R' at end
    private static @Nullable String mcVer;

    // Gale start - branding changes - version fetcher

    protected final String gitHubBranchName;
    protected final String downloadPage;
    protected final String projectDisplayName;
    protected final String organizationDisplayName;
    protected final String gitHubOrganizationName;
    protected final String gitHubRepoName;

    protected AbstractPaperVersionFetcher(String githubBranchName, String downloadPage, String projectDisplayName, String organizationDisplayName, String gitHubOrganizationName, String gitHubRepoName) {
        this.gitHubBranchName = githubBranchName;
        this.downloadPage = downloadPage;
        this.projectDisplayName = projectDisplayName;
        this.organizationDisplayName = organizationDisplayName;
        this.gitHubOrganizationName = gitHubOrganizationName;
        this.gitHubRepoName = gitHubRepoName;
    }

    // Gale end - branding changes - version fetcher

    @Override
    public long getCacheTime() {
        return 720000;
    }

    @Nonnull
    @Override
    public Component getVersionMessage(@Nonnull String serverVersion) {
        // Gale start - branding changes - version fetcher
        String[] parts = serverVersion.substring(("git-" + this.projectDisplayName + "-").length()).split("[-\\s]");
        final Component updateMessage = getUpdateStatusMessage(this.gitHubOrganizationName + "/" + this.gitHubRepoName, this.gitHubBranchName, parts[0]);
        // Gale end - branding changes - version fetcher
        final Component history = getHistory();

        return history != null ? Component.textOfChildren(updateMessage, Component.newline(), history) : updateMessage; // Leaf
    }

    protected @Nullable String getMinecraftVersion() { // Gale - branding changes - version fetcher
        if (mcVer == null) {
            java.util.regex.Matcher matcher = VER_PATTERN.matcher(org.bukkit.Bukkit.getBukkitVersion());
            if (matcher.find()) {
                String result = matcher.group();
                mcVer = result.substring(0, result.length() - 2); // strip 'R' anchor and trailing '-'
            } else {
                org.bukkit.Bukkit.getLogger().warning("Unable to match version to pattern! Report to " + this.organizationDisplayName + "!"); // Gale - branding changes - version fetcher
                org.bukkit.Bukkit.getLogger().warning("Pattern: " + VER_PATTERN.toString());
                org.bukkit.Bukkit.getLogger().warning("Version: " + org.bukkit.Bukkit.getBukkitVersion());
            }
        }

        return mcVer;
    }

    // Gale start - branding changes - version fetcher

    protected boolean canFetchDistanceFromSiteApi() {
        return false;
    }

    protected int fetchDistanceFromSiteApi(int jenkinsBuild) {
        return -1;
    }

    // Gale end - branding changes - version fetcher

    private Component getUpdateStatusMessage(@Nonnull String repo, @Nonnull String branch, @Nonnull String versionInfo) {
        // Gale start - branding changes - version fetcher
        int distance = -1;
        boolean readFromSiteApi = false;
        if (this.canFetchDistanceFromSiteApi()) {
        // Gale end - branding changes - version fetcher
        try {
            int jenkinsBuild = Integer.parseInt(versionInfo);
            // Gale start - branding changes - version fetcher
            distance = this.fetchDistanceFromSiteApi(jenkinsBuild);
            readFromSiteApi = true;
        } catch (NumberFormatException ignored) {}
        }
        if (!readFromSiteApi) {
            // Gale end - branding changes - version fetcher
            versionInfo = versionInfo.replace("\"", "");
            distance = fetchDistanceFromGitHub(repo, branch, versionInfo);
        }

        switch (distance) {
            case -1:
                return Component.text("Error obtaining version information", NamedTextColor.YELLOW);
            case 0:
                return Component.text("You are running the latest version", NamedTextColor.GREEN);
            case -2:
                return Component.text("Unknown version", NamedTextColor.YELLOW);
            default:
                return Component.text("You are " + distance + " version(s) behind", NamedTextColor.YELLOW)
                        .append(Component.newline())
                        .append(Component.text("Download the new version at: ")
                                .append(Component.text(this.downloadPage, NamedTextColor.GOLD) // Gale - branding changes - version fetcher
                                        .hoverEvent(Component.text("Click to open", NamedTextColor.WHITE))
                                        .clickEvent(ClickEvent.openUrl(this.downloadPage)))); // Gale - branding changes - version fetcher
        }
    }

    // Contributed by Techcable <Techcable@outlook.com> in GH-65
    private static int fetchDistanceFromGitHub(@Nonnull String repo, @Nonnull String branch, @Nonnull String hash) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/compare/" + branch + "..." + hash).openConnection();
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) return -2; // Unknown commit
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charsets.UTF_8))) {
                JsonObject obj = new Gson().fromJson(reader, JsonObject.class);
                String status = obj.get("status").getAsString();
                switch (status) {
                    case "identical":
                        return 0;
                    case "behind":
                        return obj.get("behind_by").getAsInt();
                    default:
                        return -1;
                }
            } catch (JsonSyntaxException | NumberFormatException e) {
                e.printStackTrace();
                return -1;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Nullable
    private Component getHistory() {
        final VersionHistoryManager.VersionData data = VersionHistoryManager.INSTANCE.getVersionData();
        if (data == null) {
            return null;
        }

        final String oldVersion = data.getOldVersion();
        if (oldVersion == null) {
            return null;
        }

        return Component.text("Previous version: " + oldVersion, NamedTextColor.GRAY, TextDecoration.ITALIC);
    }
}
