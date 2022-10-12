package org.dreeam.leaf.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class RemoveVanillaUsernameCheck implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "remove_vanilla_username_check";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("misc.remove_vanilla_username_check", """
                Remove Vanilla username check
                allowing all characters as username""");
    }
}
