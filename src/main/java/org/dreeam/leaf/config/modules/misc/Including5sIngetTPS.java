package org.dreeam.leaf.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class Including5sIngetTPS implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "including_5s_in_get_tps";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("", """
                """);
    }
}
