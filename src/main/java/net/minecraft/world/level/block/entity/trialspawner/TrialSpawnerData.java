package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;

public class TrialSpawnerData {
    public static final String TAG_SPAWN_DATA = "spawn_data";
    private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
    public static MapCodec<TrialSpawnerData> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(UUIDUtil.CODEC_SET.optionalFieldOf("registered_players", Sets.newHashSet()).forGetter((data) -> {
            return data.detectedPlayers;
        }), UUIDUtil.CODEC_SET.optionalFieldOf("current_mobs", Sets.newHashSet()).forGetter((data) -> {
            return data.currentMobs;
        }), Codec.LONG.optionalFieldOf("cooldown_ends_at", Long.valueOf(0L)).forGetter((data) -> {
            return data.cooldownEndsAt;
        }), Codec.LONG.optionalFieldOf("next_mob_spawns_at", Long.valueOf(0L)).forGetter((data) -> {
            return data.nextMobSpawnsAt;
        }), Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("total_mobs_spawned", 0).forGetter((data) -> {
            return data.totalMobsSpawned;
        }), SpawnData.CODEC.optionalFieldOf("spawn_data").forGetter((data) -> {
            return data.nextSpawnData;
        }), ResourceLocation.CODEC.optionalFieldOf("ejecting_loot_table").forGetter((data) -> {
            return data.ejectingLootTable;
        })).apply(instance, TrialSpawnerData::new);
    });
    protected final Set<UUID> detectedPlayers = new HashSet<>();
    protected final Set<UUID> currentMobs = new HashSet<>();
    public long cooldownEndsAt; // Leaves - protected -> public
    protected long nextMobSpawnsAt;
    protected int totalMobsSpawned;
    protected Optional<SpawnData> nextSpawnData;
    protected Optional<ResourceLocation> ejectingLootTable;
    protected SimpleWeightedRandomList<SpawnData> spawnPotentials;
    @Nullable
    protected Entity displayEntity;
    protected double spin;
    protected double oSpin;

    public TrialSpawnerData() {
        this(Collections.emptySet(), Collections.emptySet(), 0L, 0L, 0, Optional.empty(), Optional.empty());
    }

    public TrialSpawnerData(Set<UUID> players, Set<UUID> spawnedMobsAlive, long cooldownEnd, long nextMobSpawnsAt, int totalSpawnedMobs, Optional<SpawnData> spawnData, Optional<ResourceLocation> rewardLootTable) {
        this.detectedPlayers.addAll(players);
        this.currentMobs.addAll(spawnedMobsAlive);
        this.cooldownEndsAt = cooldownEnd;
        this.nextMobSpawnsAt = nextMobSpawnsAt;
        this.totalMobsSpawned = totalSpawnedMobs;
        this.nextSpawnData = spawnData;
        this.ejectingLootTable = rewardLootTable;
    }

    public void setSpawnPotentialsFromConfig(TrialSpawnerConfig config) {
        SimpleWeightedRandomList<SpawnData> simpleWeightedRandomList = config.spawnPotentialsDefinition();
        if (simpleWeightedRandomList.isEmpty()) {
            this.spawnPotentials = SimpleWeightedRandomList.single(this.nextSpawnData.orElseGet(SpawnData::new));
        } else {
            this.spawnPotentials = simpleWeightedRandomList;
        }

    }

    public void reset() {
        this.detectedPlayers.clear();
        this.totalMobsSpawned = 0;
        this.nextMobSpawnsAt = 0L;
        this.cooldownEndsAt = 0L;
        this.currentMobs.clear();
    }

    public boolean hasMobToSpawn() {
        boolean bl = this.nextSpawnData.isPresent() && this.nextSpawnData.get().getEntityToSpawn().contains("id", 8);
        return bl || !this.spawnPotentials.isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig config, int additionalPlayers) {
        return this.totalMobsSpawned >= config.calculateTargetTotalMobs(additionalPlayers);
    }

    public boolean haveAllCurrentMobsDied() {
        return this.currentMobs.isEmpty();
    }

    public boolean isReadyToSpawnNextMob(ServerLevel world, TrialSpawnerConfig config, int additionalPlayers) {
        return world.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < config.calculateTargetSimultaneousMobs(additionalPlayers);
    }

    public int countAdditionalPlayers(BlockPos pos) {
        if (this.detectedPlayers.isEmpty()) {
            Util.logAndPauseIfInIde("Trial Spawner at " + pos + " has no detected players");
        }

        return Math.max(0, this.detectedPlayers.size() - 1);
    }

    public void tryDetectPlayers(ServerLevel world, BlockPos pos, PlayerDetector entityDetector, int range) {
        List<UUID> list = entityDetector.detect(world, pos, range);
        boolean bl = this.detectedPlayers.addAll(list);
        if (bl) {
            this.nextMobSpawnsAt = Math.max(world.getGameTime() + 40L, this.nextMobSpawnsAt);
            world.levelEvent(3013, pos, this.detectedPlayers.size());
        }

    }

    public boolean isReadyToOpenShutter(ServerLevel world, TrialSpawnerConfig config, float position) {
        long l = this.cooldownEndsAt - (long)config.targetCooldownLength();
        return (float)world.getGameTime() >= (float)l + position;
    }

    public boolean isReadyToEjectItems(ServerLevel world, TrialSpawnerConfig config, float position) {
        long l = this.cooldownEndsAt - (long)config.targetCooldownLength();
        return (float)(world.getGameTime() - l) % position == 0.0F;
    }

    public boolean isCooldownFinished(ServerLevel world) {
        return world.getGameTime() >= this.cooldownEndsAt;
    }

    public void setEntityId(TrialSpawner logic, RandomSource random, EntityType<?> type) {
        this.getOrCreateNextSpawnData(logic, random).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    protected SpawnData getOrCreateNextSpawnData(TrialSpawner logic, RandomSource random) {
        if (this.nextSpawnData.isPresent()) {
            return this.nextSpawnData.get();
        } else {
            this.nextSpawnData = Optional.of(this.spawnPotentials.getRandom(random).map(WeightedEntry.Wrapper::getData).orElseGet(SpawnData::new));
            logic.markUpdated();
            return this.nextSpawnData.get();
        }
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(TrialSpawner logic, Level world, TrialSpawnerState state) {
        if (logic.canSpawnInLevel(world) && state.hasSpinningMob()) {
            if (this.displayEntity == null) {
                CompoundTag compoundTag = this.getOrCreateNextSpawnData(logic, world.getRandom()).getEntityToSpawn();
                if (compoundTag.contains("id", 8)) {
                    this.displayEntity = EntityType.loadEntityRecursive(compoundTag, world, Function.identity());
                }
            }

            return this.displayEntity;
        } else {
            return null;
        }
    }

    public CompoundTag getUpdateTag(TrialSpawnerState state) {
        CompoundTag compoundTag = new CompoundTag();
        if (state == TrialSpawnerState.ACTIVE) {
            compoundTag.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
        }

        this.nextSpawnData.ifPresent((spawnData) -> {
            compoundTag.put("spawn_data", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, spawnData).result().orElseThrow(() -> {
                return new IllegalStateException("Invalid SpawnData");
            }));
        });
        return compoundTag;
    }

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }
}
