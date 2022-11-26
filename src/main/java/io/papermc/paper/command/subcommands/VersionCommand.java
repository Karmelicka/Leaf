package io.papermc.paper.command.subcommands;

import io.papermc.paper.command.PaperSubcommand;
import me.titaniumtown.ArrayConstants;
import net.minecraft.server.MinecraftServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class VersionCommand implements PaperSubcommand {
    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        final @Nullable Command ver = MinecraftServer.getServer().server.getCommandMap().getCommand("version");
        if (ver != null) {
            ver.execute(sender, "paper", ArrayConstants.emptyStringArray); // Gale - JettPack - reduce array allocations
        }
        return true;
    }
}
