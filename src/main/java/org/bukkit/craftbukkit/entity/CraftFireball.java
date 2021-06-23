package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Fireball;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class CraftFireball extends AbstractProjectile implements Fireball {
    public CraftFireball(CraftServer server, AbstractHurtingProjectile entity) {
        super(server, entity);
    }

    @Override
    public float getYield() {
        return this.getHandle().bukkitYield;
    }

    @Override
    public boolean isIncendiary() {
        return this.getHandle().isIncendiary;
    }

    @Override
    public void setIsIncendiary(boolean isIncendiary) {
        this.getHandle().isIncendiary = isIncendiary;
    }

    @Override
    public void setYield(float yield) {
        this.getHandle().bukkitYield = yield;
    }

    // Paper - moved to AbstractProjectile

    @Override
    public Vector getDirection() {
        return new Vector(this.getHandle().xPower, this.getHandle().yPower, this.getHandle().zPower);
    }

    @Override
    public void setDirection(Vector direction) {
        Preconditions.checkArgument(direction != null, "Vector direction cannot be null");
        this.getHandle().setDirection(direction.getX(), direction.getY(), direction.getZ());
        this.update(); // SPIGOT-6579
    }

    @Override
    public AbstractHurtingProjectile getHandle() {
        return (AbstractHurtingProjectile) this.entity;
    }

    @Override
    public String toString() {
        return "CraftFireball";
    }
}
