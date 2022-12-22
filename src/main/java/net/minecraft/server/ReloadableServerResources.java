package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootDataManager;
import org.slf4j.Logger;

public class ReloadableServerResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final CommandBuildContext.Configurable commandBuildContext;
    public Commands commands;
    private final RecipeManager recipes = new RecipeManager();
    private final TagManager tagManager;
    private final LootDataManager lootData = new LootDataManager();
    private final ServerAdvancementManager advancements = new ServerAdvancementManager(this.lootData);
    private final ServerFunctionLibrary functionLibrary;

    public ReloadableServerResources(RegistryAccess.Frozen dynamicRegistryManager, FeatureFlagSet enabledFeatures, Commands.CommandSelection environment, int functionPermissionLevel) {
        this.tagManager = new TagManager(dynamicRegistryManager);
        this.commandBuildContext = CommandBuildContext.configurable(dynamicRegistryManager, enabledFeatures);
        this.commands = new Commands(environment, this.commandBuildContext);
        this.commandBuildContext.missingTagAccessPolicy(CommandBuildContext.MissingTagAccessPolicy.CREATE_NEW);
        this.functionLibrary = new ServerFunctionLibrary(functionPermissionLevel, this.commands.getDispatcher());
    }

    public ServerFunctionLibrary getFunctionLibrary() {
        return this.functionLibrary;
    }

    public LootDataManager getLootData() {
        return this.lootData;
    }

    public RecipeManager getRecipeManager() {
        return this.recipes;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.advancements;
    }

    public List<PreparableReloadListener> listeners() {
        return List.of(this.tagManager, this.lootData, this.recipes, this.functionLibrary, this.advancements);
    }

    public static CompletableFuture<ReloadableServerResources> loadResources(ResourceManager manager, RegistryAccess.Frozen dynamicRegistryManager, FeatureFlagSet enabledFeatures, Commands.CommandSelection environment, int functionPermissionLevel, Executor prepareExecutor, Executor applyExecutor) {
        ReloadableServerResources reloadableServerResources = new ReloadableServerResources(dynamicRegistryManager, enabledFeatures, environment, functionPermissionLevel);
        return SimpleReloadInstance.create(manager, reloadableServerResources.listeners(), prepareExecutor, applyExecutor, DATA_RELOAD_INITIAL_TASK, false).done().whenComplete((void_, throwable) -> { // Gale - Purpur - remove vanilla profiler
            reloadableServerResources.commandBuildContext.missingTagAccessPolicy(CommandBuildContext.MissingTagAccessPolicy.FAIL);
        }).thenApply((void_) -> {
            return reloadableServerResources;
        });
    }

    public void updateRegistryTags(RegistryAccess dynamicRegistryManager) {
        this.tagManager.getResult().forEach((tags) -> {
            updateRegistryTags(dynamicRegistryManager, tags);
        });
        Blocks.rebuildCache();
    }

    private static <T> void updateRegistryTags(RegistryAccess dynamicRegistryManager, TagManager.LoadResult<T> tags) {
        ResourceKey<? extends Registry<T>> resourceKey = tags.key();
        Map<TagKey<T>, List<Holder<T>>> map = tags.tags().entrySet().stream().collect(Collectors.toUnmodifiableMap((entry) -> {
            return TagKey.create(resourceKey, entry.getKey());
        }, (entry) -> {
            return List.copyOf(entry.getValue());
        }));
        dynamicRegistryManager.registryOrThrow(resourceKey).bindTags(map);
    }
}
