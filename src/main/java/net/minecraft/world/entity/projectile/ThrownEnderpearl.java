package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class ThrownEnderpearl extends ThrowableItemProjectile {

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> type, Level world) {
        super(type, world);
    }

    public ThrownEnderpearl(Level world, LivingEntity owner) {
        super(EntityType.ENDER_PEARL, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        entityHitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        for (int i = 0; i < 32; ++i) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        if (!this.level().isClientSide && !this.isRemoved()) {
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                if (entityplayer.connection.isAcceptingMessages() && entityplayer.level() == this.level() && !entityplayer.isSleeping()) {
                    // CraftBukkit start - Fire PlayerTeleportEvent
                    org.bukkit.craftbukkit.entity.CraftPlayer player = entityplayer.getBukkitEntity();
                    org.bukkit.Location location = this.getBukkitEntity().getLocation();
                    location.setPitch(player.getLocation().getPitch());
                    location.setYaw(player.getLocation().getYaw());

                    PlayerTeleportEvent teleEvent = new PlayerTeleportEvent(player, player.getLocation(), location, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
                    Bukkit.getPluginManager().callEvent(teleEvent);

                    if (!teleEvent.isCancelled() && entityplayer.connection.isAcceptingMessages()) {
                        if (this.random.nextFloat() < 0.05F && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                            Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(this.level());

                            if (entityendermite != null) {
                                entityendermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                this.level().addFreshEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                            }
                        }

                        if (entity.isPassenger()) {
                            entity.stopRiding();
                        }

                        entityplayer.connection.teleport(teleEvent.getTo());
                        entity.resetFallDistance();
                        entity.hurt(this.damageSources().fall().customCausingEntity(this), 5.0F); // CraftBukkit
                    }
                    // CraftBukkit end
                    this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
                }
            } else if (entity != null) {
                entity.teleportTo(this.getX(), this.getY(), this.getZ());
                entity.resetFallDistance();
            }

            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (entity instanceof ServerPlayer && !entity.isAlive() && this.level().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
        }

    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        Entity entity = this.getOwner();

        if (entity != null && destination != null && entity.level().dimension() != destination.dimension()) { // CraftBukkit - SPIGOT-6113
            this.setOwner((Entity) null);
        }

        return super.changeDimension(destination);
    }
}
