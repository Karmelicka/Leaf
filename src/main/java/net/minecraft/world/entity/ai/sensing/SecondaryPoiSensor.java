package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class SecondaryPoiSensor extends Sensor<Villager> {
    private static final int SCAN_RATE = 40;

    public SecondaryPoiSensor() {
        super(40);
    }

    @Override
    protected void doTick(ServerLevel world, Villager entity) {
        // Gale start - Lithium - skip secondary POI sensor if absent
        var secondaryPoi = entity.getVillagerData().getProfession().secondaryPoi();
        if (secondaryPoi == null) { // Gale - optimize villager data storage
            entity.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
            return;
        }
        // Gale end - Lithium - skip secondary POI sensor if absent
        ResourceKey<Level> resourceKey = world.dimension();
        BlockPos blockPos = entity.blockPosition();
        @Nullable ArrayList<GlobalPos> list = null; // Gale - optimize villager data storage
        int i = 4;

        for(int j = -4; j <= 4; ++j) {
            for(int k = -2; k <= 2; ++k) {
                for(int l = -4; l <= 4; ++l) {
                    BlockPos blockPos2 = blockPos.offset(j, k, l);
                    // Gale start - optimize villager data storage
                    if (secondaryPoi == world.getBlockState(blockPos2).getBlock()) {
                        if (list == null) {
                            list = Lists.newArrayList();
                        }
                        // Gale end - optimize villager data storage
                        list.add(GlobalPos.of(resourceKey, blockPos2));
                    }
                }
            }
        }

        Brain<?> brain = entity.getBrain();
        // Gale start - optimize villager data storage
        if (list != null) {
            list.trimToSize();
            // Gale end - optimize villager data storage
            brain.setMemory(MemoryModuleType.SECONDARY_JOB_SITE, list);
        } else {
            brain.eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        }

    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.SECONDARY_JOB_SITE);
    }
}
