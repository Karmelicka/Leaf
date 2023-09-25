package net.minecraft.world.entity.npc;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record VillagerProfession(String name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite, @NotNull Item @Nullable [] requestedItems, @Nullable Block secondaryPoi, @Nullable SoundEvent workSound) { // Gale - optimize villager data storage
    public static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS = (poiType) -> {
        return poiType.is(PoiTypeTags.ACQUIRABLE_JOB_SITE);
    };
    public static final VillagerProfession NONE = register("none", PoiType.NONE, ALL_ACQUIRABLE_JOBS, (SoundEvent)null);
    public static final VillagerProfession ARMORER = register("armorer", PoiTypes.ARMORER, SoundEvents.VILLAGER_WORK_ARMORER);
    public static final VillagerProfession BUTCHER = register("butcher", PoiTypes.BUTCHER, SoundEvents.VILLAGER_WORK_BUTCHER);
    public static final VillagerProfession CARTOGRAPHER = register("cartographer", PoiTypes.CARTOGRAPHER, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
    public static final VillagerProfession CLERIC = register("cleric", PoiTypes.CLERIC, ImmutableSet.of(Items.NETHER_WART), ImmutableSet.of(Blocks.SOUL_SAND), SoundEvents.VILLAGER_WORK_CLERIC); // Purpur
    public static final VillagerProfession FARMER = register("farmer", PoiTypes.FARMER, new Item[] {Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.BONE_MEAL}, Blocks.FARMLAND, SoundEvents.VILLAGER_WORK_FARMER); // Gale - optimize villager data storage
    public static final VillagerProfession FISHERMAN = register("fisherman", PoiTypes.FISHERMAN, SoundEvents.VILLAGER_WORK_FISHERMAN);
    public static final VillagerProfession FLETCHER = register("fletcher", PoiTypes.FLETCHER, SoundEvents.VILLAGER_WORK_FLETCHER);
    public static final VillagerProfession LEATHERWORKER = register("leatherworker", PoiTypes.LEATHERWORKER, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
    public static final VillagerProfession LIBRARIAN = register("librarian", PoiTypes.LIBRARIAN, SoundEvents.VILLAGER_WORK_LIBRARIAN);
    public static final VillagerProfession MASON = register("mason", PoiTypes.MASON, SoundEvents.VILLAGER_WORK_MASON);
    public static final VillagerProfession NITWIT = register("nitwit", PoiType.NONE, PoiType.NONE, (SoundEvent)null);
    public static final VillagerProfession SHEPHERD = register("shepherd", PoiTypes.SHEPHERD, SoundEvents.VILLAGER_WORK_SHEPHERD);
    public static final VillagerProfession TOOLSMITH = register("toolsmith", PoiTypes.TOOLSMITH, SoundEvents.VILLAGER_WORK_TOOLSMITH);
    public static final VillagerProfession WEAPONSMITH = register("weaponsmith", PoiTypes.WEAPONSMITH, SoundEvents.VILLAGER_WORK_WEAPONSMITH);

    @Override
    public String toString() {
        return this.name;
    }

    private static VillagerProfession register(String id, ResourceKey<PoiType> heldWorkstation, @Nullable SoundEvent workSound) {
        return register(id, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, workSound);
    }

    private static VillagerProfession register(String id, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, @Nullable SoundEvent workSound) {
        return register(id, heldWorkstation, acquirableWorkstation, null, null, workSound); // Gale - optimize villager data storage
    }

    private static VillagerProfession register(String id, ResourceKey<PoiType> heldWorkstation, @NotNull Item @Nullable [] gatherableItems, @Nullable Block secondaryJobSite, @Nullable SoundEvent workSound) { // Gale - optimize villager data storage
        return register(id, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, gatherableItems, secondaryJobSite, workSound); // Gale - optimize villager data storage
    }

    // Gale start - optimize villager data storage
    private static VillagerProfession register(String id, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, @NotNull Item @Nullable [] gatherableItems, @Nullable Block secondaryJobSite, @Nullable SoundEvent workSound) {
        return Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, new ResourceLocation(id), new VillagerProfession(id, heldWorkstation, acquirableWorkstation, gatherableItems != null && gatherableItems.length == 0 ? null : gatherableItems, secondaryJobSite, workSound));
        // Gale end - optimize villager data storage
    }
}
