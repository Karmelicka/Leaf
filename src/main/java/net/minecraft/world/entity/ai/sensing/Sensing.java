package net.minecraft.world.entity.ai.sensing;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public class Sensing {
    private final Mob mob;
    // Gale start - initialize line of sight cache with low capacity
    private final IntSet seen = new IntOpenHashSet(2);
    private final IntSet unseen = new IntOpenHashSet(2);
    // Gale end - initialize line of sight cache with low capacity

    public Sensing(Mob owner) {
        this.mob = owner;
    }

    public void tick() {
        this.seen.clear();
        this.unseen.clear();
    }

    public boolean hasLineOfSight(Entity entity) {
        int i = entity.getId();
        if (this.seen.contains(i)) {
            return true;
        } else if (this.unseen.contains(i)) {
            return false;
        } else {
            boolean bl = this.mob.hasLineOfSight(entity);
            if (bl) {
                this.seen.add(i);
            } else {
                this.unseen.add(i);
            }

            return bl;
        }
    }
}
