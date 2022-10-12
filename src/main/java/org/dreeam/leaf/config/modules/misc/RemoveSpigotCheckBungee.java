package org.dreeam.leaf.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class RemoveSpigotCheckBungee implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "remove_spigot_check_bungee_config";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("misc.remove_spigot_check_bungee_config", """
                Enable player enter backend server through proxy
                without backend server enabling its bungee mode""");
    }
}
