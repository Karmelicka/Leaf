// mc-dev import
package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootDataManager;
import org.slf4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Gson GSON = (new GsonBuilder()).create();
    public Map<ResourceLocation, AdvancementHolder> advancements = Map.of();
    private AdvancementTree tree = new AdvancementTree();
    private final LootDataManager lootData;

    public ServerAdvancementManager(LootDataManager conditionManager) {
        super(ServerAdvancementManager.GSON, "advancements");
        this.lootData = conditionManager;
    }

    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        Builder<ResourceLocation, AdvancementHolder> builder = ImmutableMap.builder();

        prepared.forEach((minecraftkey, jsonelement) -> {
            // Spigot start
            if (org.spigotmc.SpigotConfig.disabledAdvancements != null && (org.spigotmc.SpigotConfig.disabledAdvancements.contains("*") || org.spigotmc.SpigotConfig.disabledAdvancements.contains(minecraftkey.toString()) || org.spigotmc.SpigotConfig.disabledAdvancements.contains(minecraftkey.getNamespace()))) {
                return;
            }
            // Spigot end

            try {
                Advancement advancement = (Advancement) Util.getOrThrow(Advancement.CODEC.parse(JsonOps.INSTANCE, jsonelement), JsonParseException::new);

                this.validate(minecraftkey, advancement);
                builder.put(minecraftkey, new AdvancementHolder(minecraftkey, advancement));
            } catch (Exception exception) {
                ServerAdvancementManager.LOGGER.error("Parsing error loading custom advancement {}: {}", minecraftkey, exception.getMessage());
            }

        });
        this.advancements = builder.buildOrThrow();
        AdvancementTree advancementtree = new AdvancementTree();

        advancementtree.addAll(this.advancements.values());
        Iterator iterator = advancementtree.roots().iterator();

        while (iterator.hasNext()) {
            AdvancementNode advancementnode = (AdvancementNode) iterator.next();

            if (advancementnode.holder().value().display().isPresent()) {
                TreeNodePosition.run(advancementnode);
            }
        }

        this.tree = advancementtree;
    }

    private void validate(ResourceLocation id, Advancement advancement) {
        ProblemReporter.Collector problemreporter_a = new ProblemReporter.Collector();

        advancement.validate(problemreporter_a, this.lootData);
        Multimap<String, String> multimap = problemreporter_a.get();

        if (!multimap.isEmpty()) {
            String s = (String) multimap.asMap().entrySet().stream().map((entry) -> {
                String s1 = (String) entry.getKey();

                return "  at " + s1 + ": " + String.join("; ", (Iterable) entry.getValue());
            }).collect(Collectors.joining("\n"));

            ServerAdvancementManager.LOGGER.warn("Found validation problems in advancement {}: \n{}", id, s);
        }

    }

    @Nullable
    public AdvancementHolder get(ResourceLocation id) {
        return (AdvancementHolder) this.advancements.get(id);
    }

    public AdvancementTree tree() {
        return this.tree;
    }

    public Collection<AdvancementHolder> getAllAdvancements() {
        return this.advancements.values();
    }
}
