// Gale - Gale commands

package org.galemc.gale.command;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@DefaultQualifier(NonNull.class)
public interface GaleSubcommand {

    boolean execute(CommandSender sender, String subCommand, String[] args);

    default List<String> tabComplete(final CommandSender sender, final String subCommand, final String[] args) {
        return Collections.emptyList();
    }

    boolean testPermission(CommandSender sender);

    @Nullable Permission getPermission();

}
