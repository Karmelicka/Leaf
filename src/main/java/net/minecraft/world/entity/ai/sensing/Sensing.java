package net.minecraft.world.entity.ai.sensing;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.galemc.gale.configuration.GaleGlobalConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Sensing {
    private final Mob mob;
    private final Int2IntMap seen = new Int2IntOpenHashMap(2); // Gale end - initialize line of sight cache with low capacity // Gale - Petal - reduce line of sight cache lookups - merge sets

    // Gale start - Petal - reduce line of sight updates - expiring entity id lists
    private final @NotNull IntList @Nullable [] expiring;
    private int currentCacheAddIndex = 0;
    private int nextToExpireIndex = 1;
    // Gale end - Petal - reduce line of sight updates - expiring entity id lists

    public Sensing(Mob owner) {
        this.mob = owner;
        // Gale start - Petal - reduce line of sight updates - expiring entity id lists
        int updateLineOfSightInterval = GaleGlobalConfiguration.get().smallOptimizations.reducedIntervals.updateEntityLineOfSight;
        if (updateLineOfSightInterval <= 1) {
            this.expiring = null;
        } else {
            this.expiring = new IntList[updateLineOfSightInterval];
            for (int i = 0; i < updateLineOfSightInterval; i++) {
                this.expiring[i] = new IntArrayList(0);
            }
        }
        // Gale end - Petal - reduce line of sight updates - expiring entity id lists
    }

    public void tick() {
        if (this.expiring == null) { // Gale - Petal - reduce line of sight updates
        this.seen.clear();
        // Gale start - Petal - reduce line of sight updates
        } else {
            var expiringNow = this.expiring[this.nextToExpireIndex];
            expiringNow.forEach(this.seen::remove);
            expiringNow.clear();
            this.currentCacheAddIndex++;
            if (this.currentCacheAddIndex == this.expiring.length) {
                this.currentCacheAddIndex = 0;
            }
            this.nextToExpireIndex++;
            if (this.nextToExpireIndex == this.expiring.length) {
                this.nextToExpireIndex = 0;
            }
        }
        // Gale end - Petal - reduce line of sight updates
    }

    public boolean hasLineOfSight(Entity entity) {
        int i = entity.getId();
        // Gale start - Petal - reduce line of sight cache lookups - merge sets
        int cached = this.seen.get(i);
        if (cached == 1) {
            // Gale end - Petal - reduce line of sight cache lookups - merge sets
            return true;
        } else if (cached == 2) { // Gale - Petal - reduce line of sight cache lookups - merge sets
            return false;
        } else {
            boolean bl = this.mob.hasLineOfSight(entity);
            if (bl) {
                this.seen.put(i, 1); // Gale - Petal - reduce line of sight cache lookups - merge sets
            } else {
                this.seen.put(i, 2); // Gale - Petal - reduce line of sight cache lookups - merge sets
            }
            // Gale start - Petal - reduce line of sight updates
            if (this.expiring != null) {
                this.expiring[this.currentCacheAddIndex].add(i);
            }
            // Gale end - Petal - reduce line of sight updates

            return bl;
        }
    }
}
