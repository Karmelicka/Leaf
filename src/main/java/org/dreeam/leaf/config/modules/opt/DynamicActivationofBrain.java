package org.dreeam.leaf.config.modules.opt;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.DoNotLoad;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

import java.util.Collections;
import java.util.List;

public class DynamicActivationofBrain implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "dab";
    }

    @ConfigInfo(baseName = "enabled")
    public static boolean enabled = true;
    @ConfigInfo(baseName = "start-distance", comments = """
            This value determines how far away an entity has to be
            from the player to start being effected by DEAR.""")
    public static int startDistance = 12;
    @DoNotLoad
    public static int startDistanceSquared;
    @ConfigInfo(baseName = "max-tick-freq", comments = """
            This value defines how often in ticks, the furthest entity
            will get their pathfinders and behaviors ticked. 20 = 1s""")
    public static int maximumActivationPrio = 20;
    @ConfigInfo(baseName = "activation-dist-mod", comments = """
            This value defines how much distance modifies an entity's
            tick frequency. freq = (distanceToPlayer^2) / (2^value)",
            If you want further away entities to tick less often, use 7.
            If you want further away entities to tick more often, try 9.""")
    public static int activationDistanceMod = 8;
    @ConfigInfo(baseName = "blacklisted-entities", comments = "A list of entities to ignore for activation")
    public static List<String> blackedEntities = Collections.emptyList();

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("performance.dab", """
                Optimizes entity brains when
                they're far away from the player""");

        startDistanceSquared = startDistance * startDistance;
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            entityType.dabEnabled = true; // reset all, before setting the ones to true
        }
        blackedEntities.forEach(name -> EntityType.byString(name).ifPresentOrElse(entityType ->
                entityType.dabEnabled = false, () -> MinecraftServer.LOGGER.warn("Unknown entity \"{}\"", name)));
    }
}
