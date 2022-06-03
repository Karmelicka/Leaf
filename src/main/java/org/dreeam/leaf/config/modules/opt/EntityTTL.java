package org.dreeam.leaf.config.modules.opt;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

import java.util.Locale;

public class EntityTTL implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.PERFORMANCE;
    }

    @Override
    public String getBaseName() {
        return "entity_timeouts";
    }

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("performance.entity_timeouts", """
                These values define a entity's maximum lifespan. If an
                entity is in this list and it has survived for longer than
                that number of ticks, then it will be removed. Setting a value to
                -1 disables this feature.""");

        // Set some defaults
        this.get("performance.entity_timeouts.SNOWBALL", -1, config);
        this.get("performance.entity_timeouts.LLAMA_SPIT", -1, config);
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            String type = EntityType.getKey(entityType).getPath().toUpperCase(Locale.ROOT);
            entityType.ttl = this.get("performance.entity_timeouts." + type, -1, config);
        }
    }
}
