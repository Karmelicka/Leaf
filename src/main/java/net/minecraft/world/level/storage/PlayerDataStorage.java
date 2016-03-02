package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

// CraftBukkit start
import java.io.FileInputStream;
import java.io.InputStream;
import org.bukkit.craftbukkit.entity.CraftPlayer;
// CraftBukkit end

public class PlayerDataStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;
        this.playerDir = session.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try {
            CompoundTag nbttagcompound = player.saveWithoutId(new CompoundTag());
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");

            NbtIo.writeCompressed(nbttagcompound, path1);
            Path path2 = path.resolve(player.getStringUUID() + ".dat");
            Path path3 = path.resolve(player.getStringUUID() + ".dat_old");

            Util.safeReplaceFile(path2, path1, path3);
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to save player data for {}", player.getName().getString());
        }

    }

    @Nullable
    public CompoundTag load(Player player) {
        CompoundTag nbttagcompound = null;

        try {
            File file = new File(this.playerDir, player.getStringUUID() + ".dat");
            // Spigot Start
            boolean usingWrongFile = false;
            if ( org.bukkit.Bukkit.getOnlineMode() && !file.exists() ) // Paper - Check online mode first
            {
                file = new File( this.playerDir, java.util.UUID.nameUUIDFromBytes( ( "OfflinePlayer:" + player.getScoreboardName() ).getBytes( "UTF-8" ) ).toString() + ".dat");
                if ( file.exists() )
                {
                    usingWrongFile = true;
                    org.bukkit.Bukkit.getServer().getLogger().warning( "Using offline mode UUID file for player " + player.getScoreboardName() + " as it is the only copy we can find." );
                }
            }
            // Spigot End

            if (file.exists() && file.isFile()) {
                nbttagcompound = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            }
            // Spigot Start
            if ( usingWrongFile )
            {
                file.renameTo( new File( file.getPath() + ".offline-read" ) );
            }
            // Spigot End
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", player.getName().getString());
        }

        if (nbttagcompound != null) {
            // CraftBukkit start
            if (player instanceof ServerPlayer) {
                CraftPlayer player1 = (CraftPlayer) player.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, player.getUUID().toString() + ".dat").lastModified();
                if (modified < player1.getFirstPlayed()) {
                    player1.setFirstPlayed(modified);
                }
            }
            // CraftBukkit end
            int i = NbtUtils.getDataVersion(nbttagcompound, -1);

            nbttagcompound = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, nbttagcompound, i);
            player.load(nbttagcompound);
        }

        return nbttagcompound;
    }

    // CraftBukkit start
    public CompoundTag getPlayerData(String s) {
        try {
            File file1 = new File(this.playerDir, s + ".dat");

            if (file1.exists()) {
                return NbtIo.readCompressed(file1.toPath(), NbtAccounter.unlimitedHeap());
            }
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to load player data for " + s);
        }

        return null;
    }
    // CraftBukkit end

    public String[] getSeenPlayers() {
        String[] astring = this.playerDir.list();

        if (astring == null) {
            astring = new String[0];
        }

        for (int i = 0; i < astring.length; ++i) {
            if (astring[i].endsWith(".dat")) {
                astring[i] = astring[i].substring(0, astring[i].length() - 4);
            }
        }

        return astring;
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
