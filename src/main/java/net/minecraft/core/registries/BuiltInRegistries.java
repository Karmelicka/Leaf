package net.minecraft.core.registries;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.numbers.NumberFormatType;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.util.valueproviders.FloatProviderType;
import net.minecraft.util.valueproviders.IntProviderType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSources;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGenerators;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSourceType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSizeType;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacerType;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBindings;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosRuleTestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifierType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditions;
import net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProviders;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType;
import net.minecraft.world.level.storage.loot.providers.score.ScoreboardNameProviders;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class BuiltInRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, Supplier<?>> LOADERS = Maps.newLinkedHashMap();
    private static final WritableRegistry<WritableRegistry<?>> WRITABLE_REGISTRY = new MappedRegistry<>(ResourceKey.createRegistryKey(Registries.ROOT_REGISTRY_NAME), Lifecycle.stable());
    public static final DefaultedRegistry<GameEvent> GAME_EVENT = registerDefaultedWithIntrusiveHolders(Registries.GAME_EVENT, "step", (registry) -> {
        return GameEvent.STEP;
    });
    public static final Registry<SoundEvent> SOUND_EVENT = registerSimple(Registries.SOUND_EVENT, (registry) -> {
        return SoundEvents.ITEM_PICKUP;
    });
    public static final DefaultedRegistry<Fluid> FLUID = registerDefaultedWithIntrusiveHolders(Registries.FLUID, "empty", (registry) -> {
        return Fluids.EMPTY;
    });
    public static final Registry<MobEffect> MOB_EFFECT = registerSimpleWithIntrusiveHolders(Registries.MOB_EFFECT, (registry) -> {
        return MobEffects.LUCK;
    });
    public static final DefaultedRegistry<Block> BLOCK = registerDefaultedWithIntrusiveHolders(Registries.BLOCK, "air", (registry) -> {
        return Blocks.AIR;
    });
    public static final Registry<Enchantment> ENCHANTMENT = registerSimpleWithIntrusiveHolders(Registries.ENCHANTMENT, (registry) -> {
        return Enchantments.BLOCK_FORTUNE;
    });
    public static final DefaultedRegistry<EntityType<?>> ENTITY_TYPE = registerDefaultedWithIntrusiveHolders(Registries.ENTITY_TYPE, "pig", (registry) -> {
        return EntityType.PIG;
    });
    public static final DefaultedRegistry<Item> ITEM = registerDefaultedWithIntrusiveHolders(Registries.ITEM, "air", (registry) -> {
        return Items.AIR;
    });
    public static final DefaultedRegistry<Potion> POTION = registerDefaultedWithIntrusiveHolders(Registries.POTION, "empty", (registry) -> {
        return Potions.EMPTY;
    });
    public static final Registry<ParticleType<?>> PARTICLE_TYPE = registerSimple(Registries.PARTICLE_TYPE, (registry) -> {
        return ParticleTypes.BLOCK;
    });
    public static final Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPE = registerSimpleWithIntrusiveHolders(Registries.BLOCK_ENTITY_TYPE, (registry) -> {
        return BlockEntityType.FURNACE;
    });
    public static final DefaultedRegistry<PaintingVariant> PAINTING_VARIANT = registerDefaulted(Registries.PAINTING_VARIANT, "kebab", PaintingVariants::bootstrap);
    public static final Registry<ResourceLocation> CUSTOM_STAT = registerSimple(Registries.CUSTOM_STAT, (registry) -> {
        return Stats.JUMP;
    });
    public static final DefaultedRegistry<ChunkStatus> CHUNK_STATUS = registerDefaulted(Registries.CHUNK_STATUS, "empty", (registry) -> {
        return ChunkStatus.EMPTY;
    });
    public static final Registry<RuleTestType<?>> RULE_TEST = registerSimple(Registries.RULE_TEST, (registry) -> {
        return RuleTestType.ALWAYS_TRUE_TEST;
    });
    public static final Registry<RuleBlockEntityModifierType<?>> RULE_BLOCK_ENTITY_MODIFIER = registerSimple(Registries.RULE_BLOCK_ENTITY_MODIFIER, (registry) -> {
        return RuleBlockEntityModifierType.PASSTHROUGH;
    });
    public static final Registry<PosRuleTestType<?>> POS_RULE_TEST = registerSimple(Registries.POS_RULE_TEST, (registry) -> {
        return PosRuleTestType.ALWAYS_TRUE_TEST;
    });
    public static final Registry<MenuType<?>> MENU = registerSimple(Registries.MENU, (registry) -> {
        return MenuType.ANVIL;
    });
    public static final Registry<RecipeType<?>> RECIPE_TYPE = registerSimple(Registries.RECIPE_TYPE, (registry) -> {
        return RecipeType.CRAFTING;
    });
    public static final Registry<RecipeSerializer<?>> RECIPE_SERIALIZER = registerSimple(Registries.RECIPE_SERIALIZER, (registry) -> {
        return RecipeSerializer.SHAPELESS_RECIPE;
    });
    public static final Registry<Attribute> ATTRIBUTE = registerSimple(Registries.ATTRIBUTE, (registry) -> {
        return Attributes.LUCK;
    });
    public static final Registry<PositionSourceType<?>> POSITION_SOURCE_TYPE = registerSimple(Registries.POSITION_SOURCE_TYPE, (registry) -> {
        return PositionSourceType.BLOCK;
    });
    public static final Registry<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPE = registerSimple(Registries.COMMAND_ARGUMENT_TYPE, ArgumentTypeInfos::bootstrap);
    public static final Registry<StatType<?>> STAT_TYPE = registerSimple(Registries.STAT_TYPE, (registry) -> {
        return Stats.ITEM_USED;
    });
    public static final DefaultedRegistry<VillagerType> VILLAGER_TYPE = registerDefaulted(Registries.VILLAGER_TYPE, "plains", (registry) -> {
        return VillagerType.PLAINS;
    });
    public static final DefaultedRegistry<VillagerProfession> VILLAGER_PROFESSION = registerDefaulted(Registries.VILLAGER_PROFESSION, "none", (registry) -> {
        return VillagerProfession.NONE;
    });
    public static final Registry<PoiType> POINT_OF_INTEREST_TYPE = registerSimple(Registries.POINT_OF_INTEREST_TYPE, PoiTypes::bootstrap);
    public static final DefaultedRegistry<MemoryModuleType<?>> MEMORY_MODULE_TYPE = registerDefaulted(Registries.MEMORY_MODULE_TYPE, "dummy", (registry) -> {
        return MemoryModuleType.DUMMY;
    });
    public static final DefaultedRegistry<SensorType<?>> SENSOR_TYPE = registerDefaulted(Registries.SENSOR_TYPE, "dummy", (registry) -> {
        return SensorType.DUMMY;
    });
    public static final Registry<Schedule> SCHEDULE = registerSimple(Registries.SCHEDULE, (registry) -> {
        return Schedule.EMPTY;
    });
    public static final Registry<Activity> ACTIVITY = registerSimple(Registries.ACTIVITY, (registry) -> {
        return Activity.IDLE;
    });
    public static final Registry<LootPoolEntryType> LOOT_POOL_ENTRY_TYPE = registerSimple(Registries.LOOT_POOL_ENTRY_TYPE, (registry) -> {
        return LootPoolEntries.EMPTY;
    });
    public static final Registry<LootItemFunctionType> LOOT_FUNCTION_TYPE = registerSimple(Registries.LOOT_FUNCTION_TYPE, (registry) -> {
        return LootItemFunctions.SET_COUNT;
    });
    public static final Registry<LootItemConditionType> LOOT_CONDITION_TYPE = registerSimple(Registries.LOOT_CONDITION_TYPE, (registry) -> {
        return LootItemConditions.INVERTED;
    });
    public static final Registry<LootNumberProviderType> LOOT_NUMBER_PROVIDER_TYPE = registerSimple(Registries.LOOT_NUMBER_PROVIDER_TYPE, (registry) -> {
        return NumberProviders.CONSTANT;
    });
    public static final Registry<LootNbtProviderType> LOOT_NBT_PROVIDER_TYPE = registerSimple(Registries.LOOT_NBT_PROVIDER_TYPE, (registry) -> {
        return NbtProviders.CONTEXT;
    });
    public static final Registry<LootScoreProviderType> LOOT_SCORE_PROVIDER_TYPE = registerSimple(Registries.LOOT_SCORE_PROVIDER_TYPE, (registry) -> {
        return ScoreboardNameProviders.CONTEXT;
    });
    public static final Registry<FloatProviderType<?>> FLOAT_PROVIDER_TYPE = registerSimple(Registries.FLOAT_PROVIDER_TYPE, (registry) -> {
        return FloatProviderType.CONSTANT;
    });
    public static final Registry<IntProviderType<?>> INT_PROVIDER_TYPE = registerSimple(Registries.INT_PROVIDER_TYPE, (registry) -> {
        return IntProviderType.CONSTANT;
    });
    public static final Registry<HeightProviderType<?>> HEIGHT_PROVIDER_TYPE = registerSimple(Registries.HEIGHT_PROVIDER_TYPE, (registry) -> {
        return HeightProviderType.CONSTANT;
    });
    public static final Registry<BlockPredicateType<?>> BLOCK_PREDICATE_TYPE = registerSimple(Registries.BLOCK_PREDICATE_TYPE, (registry) -> {
        return BlockPredicateType.NOT;
    });
    public static final Registry<WorldCarver<?>> CARVER = registerSimple(Registries.CARVER, (registry) -> {
        return WorldCarver.CAVE;
    });
    public static final Registry<Feature<?>> FEATURE = registerSimple(Registries.FEATURE, (registry) -> {
        return Feature.ORE;
    });
    public static final Registry<StructurePlacementType<?>> STRUCTURE_PLACEMENT = registerSimple(Registries.STRUCTURE_PLACEMENT, (registry) -> {
        return StructurePlacementType.RANDOM_SPREAD;
    });
    public static final Registry<StructurePieceType> STRUCTURE_PIECE = registerSimple(Registries.STRUCTURE_PIECE, (registry) -> {
        return StructurePieceType.MINE_SHAFT_ROOM;
    });
    public static final Registry<StructureType<?>> STRUCTURE_TYPE = registerSimple(Registries.STRUCTURE_TYPE, (registry) -> {
        return StructureType.JIGSAW;
    });
    public static final Registry<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPE = registerSimple(Registries.PLACEMENT_MODIFIER_TYPE, (registry) -> {
        return PlacementModifierType.COUNT;
    });
    public static final Registry<BlockStateProviderType<?>> BLOCKSTATE_PROVIDER_TYPE = registerSimple(Registries.BLOCK_STATE_PROVIDER_TYPE, (registry) -> {
        return BlockStateProviderType.SIMPLE_STATE_PROVIDER;
    });
    public static final Registry<FoliagePlacerType<?>> FOLIAGE_PLACER_TYPE = registerSimple(Registries.FOLIAGE_PLACER_TYPE, (registry) -> {
        return FoliagePlacerType.BLOB_FOLIAGE_PLACER;
    });
    public static final Registry<TrunkPlacerType<?>> TRUNK_PLACER_TYPE = registerSimple(Registries.TRUNK_PLACER_TYPE, (registry) -> {
        return TrunkPlacerType.STRAIGHT_TRUNK_PLACER;
    });
    public static final Registry<RootPlacerType<?>> ROOT_PLACER_TYPE = registerSimple(Registries.ROOT_PLACER_TYPE, (registry) -> {
        return RootPlacerType.MANGROVE_ROOT_PLACER;
    });
    public static final Registry<TreeDecoratorType<?>> TREE_DECORATOR_TYPE = registerSimple(Registries.TREE_DECORATOR_TYPE, (registry) -> {
        return TreeDecoratorType.LEAVE_VINE;
    });
    public static final Registry<FeatureSizeType<?>> FEATURE_SIZE_TYPE = registerSimple(Registries.FEATURE_SIZE_TYPE, (registry) -> {
        return FeatureSizeType.TWO_LAYERS_FEATURE_SIZE;
    });
    public static final Registry<Codec<? extends BiomeSource>> BIOME_SOURCE = registerSimple(Registries.BIOME_SOURCE, Lifecycle.stable(), BiomeSources::bootstrap);
    public static final Registry<Codec<? extends ChunkGenerator>> CHUNK_GENERATOR = registerSimple(Registries.CHUNK_GENERATOR, Lifecycle.stable(), ChunkGenerators::bootstrap);
    public static final Registry<Codec<? extends SurfaceRules.ConditionSource>> MATERIAL_CONDITION = registerSimple(Registries.MATERIAL_CONDITION, SurfaceRules.ConditionSource::bootstrap);
    public static final Registry<Codec<? extends SurfaceRules.RuleSource>> MATERIAL_RULE = registerSimple(Registries.MATERIAL_RULE, SurfaceRules.RuleSource::bootstrap);
    public static final Registry<Codec<? extends DensityFunction>> DENSITY_FUNCTION_TYPE = registerSimple(Registries.DENSITY_FUNCTION_TYPE, DensityFunctions::bootstrap);
    public static final Registry<MapCodec<? extends Block>> BLOCK_TYPE = registerSimple(Registries.BLOCK_TYPE, BlockTypes::bootstrap);
    public static final Registry<StructureProcessorType<?>> STRUCTURE_PROCESSOR = registerSimple(Registries.STRUCTURE_PROCESSOR, (registry) -> {
        return StructureProcessorType.BLOCK_IGNORE;
    });
    public static final Registry<StructurePoolElementType<?>> STRUCTURE_POOL_ELEMENT = registerSimple(Registries.STRUCTURE_POOL_ELEMENT, (registry) -> {
        return StructurePoolElementType.EMPTY;
    });
    public static final Registry<Codec<? extends PoolAliasBinding>> POOL_ALIAS_BINDING_TYPE = registerSimple(Registries.POOL_ALIAS_BINDING, PoolAliasBindings::bootstrap);
    public static final Registry<CatVariant> CAT_VARIANT = registerSimple(Registries.CAT_VARIANT, CatVariant::bootstrap);
    public static final Registry<FrogVariant> FROG_VARIANT = registerSimple(Registries.FROG_VARIANT, (registry) -> {
        return FrogVariant.TEMPERATE;
    });
    public static final Registry<BannerPattern> BANNER_PATTERN = registerSimple(Registries.BANNER_PATTERN, BannerPatterns::bootstrap);
    public static final Registry<Instrument> INSTRUMENT = registerSimple(Registries.INSTRUMENT, Instruments::bootstrap);
    public static final Registry<String> DECORATED_POT_PATTERNS = registerSimple(Registries.DECORATED_POT_PATTERNS, DecoratedPotPatterns::bootstrap);
    public static final Registry<CreativeModeTab> CREATIVE_MODE_TAB = registerSimple(Registries.CREATIVE_MODE_TAB, CreativeModeTabs::bootstrap);
    public static final Registry<CriterionTrigger<?>> TRIGGER_TYPES = registerSimple(Registries.TRIGGER_TYPE, CriteriaTriggers::bootstrap);
    public static final Registry<NumberFormatType<?>> NUMBER_FORMAT_TYPE = registerSimple(Registries.NUMBER_FORMAT_TYPE, NumberFormatTypes::bootstrap);
    public static final Registry<? extends Registry<?>> REGISTRY = WRITABLE_REGISTRY;

    private static <T> Registry<T> registerSimple(ResourceKey<? extends Registry<T>> key, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return registerSimple(key, Lifecycle.stable(), initializer);
    }

    private static <T> Registry<T> registerSimpleWithIntrusiveHolders(ResourceKey<? extends Registry<T>> key, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return internalRegister(key, new MappedRegistry<>(key, Lifecycle.stable(), true), initializer, Lifecycle.stable());
    }

    private static <T> DefaultedRegistry<T> registerDefaulted(ResourceKey<? extends Registry<T>> key, String defaultId, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return registerDefaulted(key, defaultId, Lifecycle.stable(), initializer);
    }

    private static <T> DefaultedRegistry<T> registerDefaultedWithIntrusiveHolders(ResourceKey<? extends Registry<T>> key, String defaultId, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return registerDefaultedWithIntrusiveHolders(key, defaultId, Lifecycle.stable(), initializer);
    }

    private static <T> Registry<T> registerSimple(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return internalRegister(key, new MappedRegistry<>(key, lifecycle, false), initializer, lifecycle);
    }

    private static <T> DefaultedRegistry<T> registerDefaulted(ResourceKey<? extends Registry<T>> key, String defaultId, Lifecycle lifecycle, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return internalRegister(key, new DefaultedMappedRegistry<>(defaultId, key, lifecycle, false), initializer, lifecycle);
    }

    private static <T> DefaultedRegistry<T> registerDefaultedWithIntrusiveHolders(ResourceKey<? extends Registry<T>> key, String defaultId, Lifecycle lifecycle, BuiltInRegistries.RegistryBootstrap<T> initializer) {
        return internalRegister(key, new DefaultedMappedRegistry<>(defaultId, key, lifecycle, true), initializer, lifecycle);
    }

    private static <T, R extends WritableRegistry<T>> R internalRegister(ResourceKey<? extends Registry<T>> key, R registry, BuiltInRegistries.RegistryBootstrap<T> initializer, Lifecycle lifecycle) {
        Bootstrap.checkBootstrapCalled(() -> {
            return "registry " + key;
        });
        ResourceLocation resourceLocation = key.location();
        LOADERS.put(resourceLocation, () -> {
            return initializer.run(registry);
        });
        WRITABLE_REGISTRY.register((ResourceKey) key, registry, lifecycle); // Paper - decompile fix
        return registry;
    }

    public static void bootStrap() {
        // Paper start
        bootStrap(() -> {});
    }
    public static void bootStrap(Runnable runnable) {
        // Paper end
        createContents();
        runnable.run(); // Paper
        freeze();
        validate(REGISTRY);
    }

    private static void createContents() {
        LOADERS.forEach((id, initializer) -> {
            if (initializer.get() == null) {
                LOGGER.error("Unable to bootstrap registry '{}'", (Object)id);
            }

        });
    }

    private static void freeze() {
        REGISTRY.freeze();

        for(Registry<?> registry : REGISTRY) {
            registry.freeze();
        }

    }

    private static <T extends Registry<?>> void validate(Registry<T> registries) {
        registries.forEach((registry) -> {
            if (registry.keySet().isEmpty()) {
                Util.logAndPauseIfInIde("Registry '" + registries.getKey(registry) + "' was empty after loading");
            }

            if (registry instanceof DefaultedRegistry) {
                ResourceLocation resourceLocation = ((DefaultedRegistry)registry).getDefaultKey();
                Validate.notNull(registry.get(resourceLocation), "Missing default of DefaultedMappedRegistry: " + resourceLocation);
            }

        });
    }

    @FunctionalInterface
    interface RegistryBootstrap<T> {
        T run(Registry<T> registry);
    }
}
