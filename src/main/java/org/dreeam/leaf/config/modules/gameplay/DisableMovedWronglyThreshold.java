package org.dreeam.leaf.config.modules.gameplay;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class DisableMovedWronglyThreshold implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.GAMEPLAY;
    }

    @Override
    public String getBaseName() {
        return "disable_moved_wrongly_threshold";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = false;

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("gameplay.disable_moved_wrongly_threshold", "Disable moved quickly/wrongly checks");
    }
}
