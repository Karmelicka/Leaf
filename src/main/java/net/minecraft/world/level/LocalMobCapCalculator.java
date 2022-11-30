package net.minecraft.world.level;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

public class LocalMobCapCalculator {
    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectOpenHashMap<>();
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = Maps.newHashMap();
    private final ChunkMap chunkMap;

    public LocalMobCapCalculator(ChunkMap threadedAnvilChunkStorage) {
        this.chunkMap = threadedAnvilChunkStorage;
    }

    private List<ServerPlayer> getPlayersNear(ChunkPos chunkPos) {
        return this.playersNearChunk.computeIfAbsent(chunkPos.toLong(), (pos) -> {
            return this.chunkMap.getPlayersCloseForSpawning(chunkPos);
        });
    }

    public void addMob(ChunkPos chunkPos, MobCategory spawnGroup) {
        for(ServerPlayer serverPlayer : this.getPlayersNear(chunkPos)) {
            this.playerMobCounts.computeIfAbsent(serverPlayer, (player) -> {
                return new LocalMobCapCalculator.MobCounts();
            }).add(spawnGroup);
        }

    }

    public boolean canSpawn(MobCategory spawnGroup, ChunkPos chunkPos) {
        for(ServerPlayer serverPlayer : this.getPlayersNear(chunkPos)) {
            LocalMobCapCalculator.MobCounts mobCounts = this.playerMobCounts.get(serverPlayer);
            if (mobCounts == null || mobCounts.canSpawn(spawnGroup)) {
                return true;
            }
        }

        return false;
    }

    static class MobCounts {
        public final int[] counts = new int[MobCategory.values().length]; // Gale - VMP - store mob counts in an array

        public void add(MobCategory spawnGroup) {
            this.counts[spawnGroup.ordinal()]++; // Gale - VMP - store mob counts in an array
        }

        public boolean canSpawn(MobCategory spawnGroup) {
            return this.counts[spawnGroup.ordinal()] < spawnGroup.getMaxInstancesPerChunk(); // Gale - VMP - store mob counts in an array
        }
    }
}
