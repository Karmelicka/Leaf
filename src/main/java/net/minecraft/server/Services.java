package net.minecraft.server;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.io.File;
import javax.annotation.Nullable;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.SignatureValidator;
import org.galemc.gale.configuration.GaleConfigurations;

// Paper start - add paper configuration files
public record Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, GameProfileCache profileCache, @javax.annotation.Nullable io.papermc.paper.configuration.PaperConfigurations paperConfigurations, @javax.annotation.Nullable GaleConfigurations galeConfigurations) { // Gale - Gale configuration

    public Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, GameProfileCache profileCache) {
        this(sessionService, servicesKeySet, profileRepository, profileCache, null, null); // Gale - Gale configuration
    }

    @Override
    public io.papermc.paper.configuration.PaperConfigurations paperConfigurations() {
        return java.util.Objects.requireNonNull(this.paperConfigurations);
    }
    // Paper end - add paper configuration files
    // Gale start - Gale configuration
    public GaleConfigurations galeConfigurations() {
        return java.util.Objects.requireNonNull(this.galeConfigurations);
    }
    // Gale end - Gale configuration
    public static final String USERID_CACHE_FILE = "usercache.json"; // Paper - private -> public

    public static Services create(YggdrasilAuthenticationService authenticationService, File rootDirectory, File userCacheFile, joptsimple.OptionSet optionSet) throws Exception { // Paper - add optionset to load paper config files; add userCacheFile parameter
        MinecraftSessionService minecraftSessionService = authenticationService.createMinecraftSessionService();
        GameProfileRepository gameProfileRepository = authenticationService.createProfileRepository();
        GameProfileCache gameProfileCache = new GameProfileCache(gameProfileRepository, userCacheFile); // Paper - use specified user cache file
        // Paper start - load paper config files from cli options
        final java.nio.file.Path legacyConfigPath = ((File) optionSet.valueOf("paper-settings")).toPath();
        final java.nio.file.Path configDirPath = ((File) optionSet.valueOf("paper-settings-directory")).toPath();
        io.papermc.paper.configuration.PaperConfigurations paperConfigurations = io.papermc.paper.configuration.PaperConfigurations.setup(legacyConfigPath, configDirPath, rootDirectory.toPath(), (File) optionSet.valueOf("spigot-settings"));
        // Gale start - Gale configuration
        GaleConfigurations galeConfigurations = GaleConfigurations.setup(configDirPath);
        return new Services(minecraftSessionService, authenticationService.getServicesKeySet(), gameProfileRepository, gameProfileCache, paperConfigurations, galeConfigurations);
        // Gale end - Gale configuration
        // Paper end - load paper config files from cli options
    }

    @Nullable
    public SignatureValidator profileKeySignatureValidator() {
        return SignatureValidator.from(this.servicesKeySet, ServicesKeyType.PROFILE_KEY);
    }

    public boolean canValidateProfileKeys() {
        return !this.servicesKeySet.keys(ServicesKeyType.PROFILE_KEY).isEmpty();
    }
}
