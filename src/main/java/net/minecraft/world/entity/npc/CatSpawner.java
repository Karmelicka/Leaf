package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {
    private static final int TICK_DELAY = 1200;
    private int nextTick;

    @Override
    public int tick(ServerLevel world, boolean spawnMonsters, boolean spawnAnimals) {
        if (spawnAnimals && world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            --this.nextTick;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick = world.purpurConfig.catSpawnDelay; // Purpur
                Player player = world.getRandomPlayer();
                if (player == null) {
                    return 0;
                } else {
                    RandomSource randomSource = world.random;
                    int i = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                    int j = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                    BlockPos blockPos = player.blockPosition().offset(i, 0, j);
                    int k = 10;
                    if (!world.hasChunksAt(blockPos.getX() - 10, blockPos.getZ() - 10, blockPos.getX() + 10, blockPos.getZ() + 10)) {
                        return 0;
                    } else {
                        if (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, world, blockPos, EntityType.CAT)) {
                            if (world.isCloseToVillage(blockPos, 2)) {
                                return this.spawnInVillage(world, blockPos);
                            }

                            if (world.structureManager().getStructureWithPieceAt(blockPos, StructureTags.CATS_SPAWN_IN).isValid()) {
                                return this.spawnInHut(world, blockPos);
                            }
                        }

                        return 0;
                    }
                }
            }
        } else {
            return 0;
        }
    }

    private int spawnInVillage(ServerLevel world, BlockPos pos) {
        // Purpur start
        int range = world.purpurConfig.catSpawnVillageScanRange;
        if (range <= 0) return 0;

        if (world.getPoiManager().getCountInRange((entry) -> {
            return entry.is(PoiTypes.HOME);
        }, pos, range, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> list = world.getEntitiesOfClass(Cat.class, (new AABB(pos)).inflate(range, 8.0D, range));
            // Purpur end
            if (list.size() < 5) {
                return this.spawnCat(pos, world);
            }
        }

        return 0;
    }

    private int spawnInHut(ServerLevel world, BlockPos pos) {
        // Purpur start
        int range = world.purpurConfig.catSpawnSwampHutScanRange;
        if (range <= 0) return 0;
        List<Cat> list = world.getEntitiesOfClass(Cat.class, (new AABB(pos)).inflate(range, 8.0D, range));
        // Purpur end
        return list.size() < 1 ? this.spawnCat(pos, world) : 0;
    }

    private int spawnCat(BlockPos pos, ServerLevel world) {
        Cat cat = EntityType.CAT.create(world);
        if (cat == null) {
            return 0;
        } else {
            cat.moveTo(pos, 0.0F, 0.0F); // Paper - move up - Fix MC-147659
            cat.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, (SpawnGroupData)null, (CompoundTag)null);
            world.addFreshEntityWithPassengers(cat);
            return 1;
        }
    }
}
