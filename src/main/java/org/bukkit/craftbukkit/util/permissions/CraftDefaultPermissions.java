package org.bukkit.craftbukkit.util.permissions;

import org.bukkit.permissions.Permission;
import org.bukkit.util.permissions.DefaultPermissions;

public final class CraftDefaultPermissions {
    private static final String ROOT = "minecraft";
    public static final String GALE_ROOT = "gale"; // Gale - set Gale permissions root

    public static final String writeBooks = GALE_ROOT + ".writebooks"; // Gale - Pufferfish - make book writing configurable

    private CraftDefaultPermissions() {}

    public static void registerCorePermissions() {
        Permission parent = DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT, "Gives the user the ability to use all vanilla utilities and commands");
        CommandPermissions.registerPermissions(parent);
        // Spigot start
        DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT + ".nbt.place", "Gives the user the ability to place restricted blocks with NBT in creative", org.bukkit.permissions.PermissionDefault.OP, parent);
        DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT + ".nbt.copy", "Gives the user the ability to copy NBT in creative", org.bukkit.permissions.PermissionDefault.TRUE, parent);
        DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT + ".debugstick", "Gives the user the ability to use the debug stick in creative", org.bukkit.permissions.PermissionDefault.OP, parent);
        DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT + ".debugstick.always", "Gives the user the ability to use the debug stick in all game modes", org.bukkit.permissions.PermissionDefault.FALSE/* , parent */); // Paper - should not have this parent, as it's not a "vanilla" utility
        DefaultPermissions.registerPermission(CraftDefaultPermissions.ROOT + ".commandblock", "Gives the user the ability to use command blocks.", org.bukkit.permissions.PermissionDefault.OP, parent); // Paper
        // Spigot end
        DefaultPermissions.registerPermission(writeBooks, "Gives the user the ability to write books even when writing books is disabled in the Gale configuration", org.bukkit.permissions.PermissionDefault.OP); // Gale - Pufferfish - make book writing configurable
        parent.recalculatePermissibles();
    }
}
