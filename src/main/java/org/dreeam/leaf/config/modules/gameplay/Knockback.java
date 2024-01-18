package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class Knockback implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.GAMEPLAY;
    }

    @Override
    public String getBaseName() {
        return "knockback";
    }

    @ConfigInfo(baseName = "snowball-knockback-players", comments = "Make snowball can knockback players")
    public static boolean snowballCanKnockback = false;

    @ConfigInfo(baseName = "egg-knockback-players", comments = "Make egg can knockback players")
    public static boolean eggCanKnockback = false;
}
